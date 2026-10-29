package com.custom.iptvtv.net

import com.custom.iptvtv.AppState
import com.custom.iptvtv.data.MediaItem
import com.custom.iptvtv.data.Xtream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Telefon/PC'den liste eklemek için geçici yerel web sunucusu.
 * Yalnızca Ayarlar'da açılır, ekrandan çıkınca kapanır. Her açılışta yeni 4 haneli PIN üretilir;
 * 5 hatalı denemeden sonra oturum kilitlenir.
 */
object RemoteServer {
    class Info(val url: String, val pin: String)

    @Volatile private var server: ServerSocket? = null
    @Volatile private var pool: ExecutorService? = null
    @Volatile private var pin = ""
    private val failures = AtomicInteger(0)

    fun start(state: AppState): Info? {
        stop()
        val ip = localIp() ?: return null
        val ss = bind() ?: return null
        pin = "%04d".format(SecureRandom().nextInt(10000))
        failures.set(0)
        val p = Executors.newFixedThreadPool(4)
        server = ss
        pool = p
        thread(isDaemon = true, name = "remote-accept") {
            try {
                while (!ss.isClosed) {
                    val c = ss.accept()
                    p.execute {
                        runCatching { handle(c, state) }
                        runCatching { c.close() }
                    }
                }
            } catch (_: Exception) {
                // sunucu kapatıldı
            }
        }
        return Info("http://$ip:${ss.localPort}", pin)
    }

    fun stop() {
        val ss = server
        val p = pool
        server = null
        pool = null
        pin = ""
        if (ss != null || p != null) {
            thread(isDaemon = true) {
                runCatching { ss?.close() }
                runCatching { p?.shutdownNow() }
            }
        }
    }

    private fun bind(): ServerSocket? {
        for (port in 8080..8090) {
            try { return ServerSocket(port) } catch (_: IOException) { }
        }
        return null
    }

    private fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    // ---------------- HTTP ----------------

    private fun handle(c: Socket, state: AppState) {
        c.soTimeout = 5000
        val input = BufferedInputStream(c.getInputStream())
        val out = c.getOutputStream()
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1].substringBefore('?')

        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len > 16 * 1024) { json(out, 413, JSONObject().put("ok", false).put("error", "İstek çok büyük")); return }
        val body = if (len > 0) {
            val b = ByteArray(len)
            var read = 0
            while (read < len) {
                val n = input.read(b, read, len - read)
                if (n < 0) break
                read += n
            }
            String(b, 0, read, Charsets.UTF_8)
        } else ""

        when {
            method == "GET" && (path == "/" || path == "/index.html") -> respond(out, 200, "text/html; charset=utf-8", PAGE)
            path == "/api/sources" && method == "GET" -> api(out, headers) {
                val arr = JSONArray()
                state.sources.forEach {
                    arr.put(JSONObject().put("id", MediaItem.stableId(it.url)).put("name", it.name).put("url", mask(it.url)))
                }
                JSONObject().put("ok", true).put("sources", arr)
            }
            path == "/api/add" && method == "POST" -> api(out, headers) {
                val o = JSONObject(body)
                val url = if (o.optString("mode") == "xtream") {
                    Xtream.buildUrl(o.optString("host"), o.optString("user"), o.optString("pass"), o.optString("output", "ts"))
                        ?: return@api JSONObject().put("ok", false).put("error", "Sunucu, kullanıcı adı ve şifre gerekli")
                } else o.optString("url")
                val err = runBlocking { withContext(Dispatchers.Main) { state.tryAddSource(o.optString("name"), url) } }
                if (err == null) JSONObject().put("ok", true) else JSONObject().put("ok", false).put("error", err)
            }
            path == "/api/remove" && method == "POST" -> api(out, headers) {
                val id = JSONObject(body).optString("id")
                val s = state.sources.firstOrNull { MediaItem.stableId(it.url) == id }
                    ?: return@api JSONObject().put("ok", false).put("error", "Kaynak bulunamadı")
                runBlocking { withContext(Dispatchers.Main) { state.removeSource(s) } }
                JSONObject().put("ok", true)
            }
            else -> respond(out, 404, "text/plain; charset=utf-8", "Bulunamadı")
        }
    }

    /** PIN doğrulaması + hata yakalama; sonucu JSON olarak yazar. */
    private fun api(out: OutputStream, headers: Map<String, String>, block: () -> JSONObject) {
        if (failures.get() >= 5) {
            json(out, 429, JSONObject().put("ok", false).put("error", "Çok fazla hatalı deneme. TV'de paneli kapatıp yeniden aç."))
            return
        }
        val given = (headers["x-pin"] ?: "").toByteArray()
        val ok = pin.isNotEmpty() && MessageDigest.isEqual(given, pin.toByteArray())
        if (!ok) {
            failures.incrementAndGet()
            json(out, 401, JSONObject().put("ok", false).put("error", "PIN hatalı (TV ekranındaki 4 haneli kod)"))
            return
        }
        failures.set(0)
        val res = try { block() } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", "İşlem başarısız: " + (e.message ?: e.javaClass.simpleName))
        }
        json(out, 200, res)
    }

    private fun json(out: OutputStream, code: Int, o: JSONObject) =
        respond(out, code, "application/json; charset=utf-8", o.toString())

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"; 401 -> "Unauthorized"; 404 -> "Not Found"; 413 -> "Payload Too Large"; 429 -> "Too Many Requests"; else -> "Error"
        }
        val head = "HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    private fun readLine(i: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = i.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                sb.append(b.toChar())
                if (sb.length > 8192) return null
            }
        }
        return sb.toString()
    }

    private fun mask(url: String): String =
        url.replace(Regex("(?i)(password|pass|pwd|token|key)=[^&]+"), "$1=•••")
            .replace(Regex("(?i)(https?://)[^/@]+@"), "$1•••@")

    // Not: Kotlin ham metninde '$' kullanma (şablon sanılır); sayfadaki betik '$' içermez.
    private val PAGE = """<!doctype html>
<html lang="tr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>NextStar IPTV</title>
<style>
body{margin:0;font-family:system-ui,sans-serif;background:#0a0e15;color:#eaf0f7}
.w{max-width:520px;margin:0 auto;padding:20px}
h1{font-size:22px;margin:8px 0 4px}
p{color:#8793a5;margin:0 0 16px;font-size:14px}
.card{background:#141c28;border-radius:14px;padding:16px;margin-bottom:16px}
label{display:block;font-size:13px;color:#8793a5;margin:12px 0 4px}
input,select{width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:1px solid #2a3850;background:#0a0e15;color:#eaf0f7;font-size:16px}
button{width:100%;margin-top:16px;padding:14px;border:0;border-radius:10px;background:#3da5ff;color:#04121f;font-size:16px;font-weight:700}
.tabs{display:flex;gap:8px;margin-bottom:4px}
.tabs button{margin:0;background:#1f2b3d;color:#eaf0f7;padding:10px}
.tabs button.on{background:#3da5ff;color:#04121f}
.msg{margin-top:12px;font-size:14px}
.ok{color:#5be28a}.err{color:#ff6b6b}
.row{display:flex;align-items:center;gap:8px;padding:8px 0;border-top:1px solid #1f2b3d}
.row div{flex:1;min-width:0;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.row small{color:#8793a5;display:block;overflow:hidden;text-overflow:ellipsis}
.row button{width:auto;margin:0;padding:8px 12px;background:#3a2330;color:#ff6b6b;font-size:13px}
</style></head><body><div class="w">
<h1>NextStar IPTV</h1>
<p>Liste kaynağını telefondan veya bilgisayardan ekle.</p>
<div class="card">
<label>TV ekranındaki PIN</label>
<input id="pin" inputmode="numeric" maxlength="4" placeholder="0000" oninput="if(pin().length==4)list()">
</div>
<div class="card">
<div class="tabs"><button id="t1" class="on" onclick="tab('xtream')">Xtream Codes</button><button id="t2" onclick="tab('m3u')">M3U adresi</button></div>
<div id="f1">
<label>Sunucu (varsa port ile)</label><input id="xh" placeholder="http://1.2.3.4:8080" autocapitalize="off" autocorrect="off">
<label>Kullanıcı adı</label><input id="xu" autocapitalize="off" autocorrect="off">
<label>Şifre</label><input id="xp" autocapitalize="off" autocorrect="off">
<label>Akış biçimi</label><select id="xo"><option value="ts">ts (varsayılan)</option><option value="m3u8">m3u8 (HLS)</option></select>
<label>Ad (isteğe bağlı)</label><input id="xn">
</div>
<div id="f2" style="display:none">
<label>M3U / JSON / GitHub raw adresi</label><input id="mu" placeholder="https://..." autocapitalize="off" autocorrect="off">
<label>Ad (isteğe bağlı)</label><input id="mn">
</div>
<button onclick="add()">Ekle</button>
<div id="msg" class="msg"></div>
</div>
<div class="card"><b>Ekli kaynaklar</b><div id="list"></div></div>
</div>
<script>
function g(id){return document.getElementById(id)}
function pin(){return g("pin").value.trim()}
var mode="xtream";
function tab(m){mode=m;g("t1").className=m=="xtream"?"on":"";g("t2").className=m=="m3u"?"on":"";g("f1").style.display=m=="xtream"?"block":"none";g("f2").style.display=m=="m3u"?"block":"none"}
function say(t,ok){var e=g("msg");e.textContent=t;e.className="msg "+(ok?"ok":"err")}
function api(path,method,body){return fetch(path,{method:method,headers:{"X-Pin":pin(),"Content-Type":"application/json"},body:body?JSON.stringify(body):undefined}).then(function(r){return r.json()})}
function add(){
var b=mode=="xtream"?{mode:"xtream",name:g("xn").value,host:g("xh").value,user:g("xu").value,pass:g("xp").value,output:g("xo").value}:{mode:"m3u",name:g("mn").value,url:g("mu").value};
api("/api/add","POST",b).then(function(j){if(j.ok){say("Eklendi. TV listeyi indiriyor…",true);list()}else say(j.error||"Hata",false)}).catch(function(){say("TV'ye ulaşılamadı. Aynı Wi-Fi ağında mısın?",false)})
}
function list(){
api("/api/sources","GET").then(function(j){
var box=g("list");box.textContent="";
if(!j.ok){say(j.error||"Hata",false);return}
(j.sources||[]).forEach(function(s){
var r=document.createElement("div");r.className="row";
var d=document.createElement("div");d.textContent=s.name;
var sm=document.createElement("small");sm.textContent=s.url;d.appendChild(sm);
var bt=document.createElement("button");bt.textContent="Sil";
bt.onclick=function(){if(confirm(s.name+" silinsin mi?"))api("/api/remove","POST",{id:s.id}).then(list)};
r.appendChild(d);r.appendChild(bt);box.appendChild(r)
})
}).catch(function(){})
}
</script></body></html>"""
}
