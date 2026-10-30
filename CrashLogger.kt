package com.custom.iptvtv

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Uygulama çökerse yığın izini dosyaya yazar; bir sonraki açılışta Ana Sayfa ve Ayarlar'da gösterilir. */
object CrashLogger {
    private const val FILE = "last_crash.txt"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val heapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                val usedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
                val text = buildString {
                    append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    append("  thread=").append(thread.name)
                    append("  bellek=").append(usedMb).append('/').append(heapMb).append("MB\n")
                    append(Log.getStackTraceString(error))
                }
                File(app.filesDir, FILE).writeText(text.take(9000))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
