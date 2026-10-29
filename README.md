# NextStar IPTV (v2)

Android TV / Google TV için sıfırdan yazılmış IPTV ve medya oynatıcı. Kotlin, Jetpack Compose, Media3 (ExoPlayer), OkHttp (DoH), Coil.

## APK nasıl alınır

**Yol 1 – GitHub Actions (önerilen, bilgisayarda kurulum gerekmez)**
1. Bu klasörün içeriğini yeni bir GitHub deposuna yükle (`main` dalı).
2. Depoda *Actions → Build APK → Run workflow* (push de otomatik tetikler).
3. İş bitince deponun sağındaki *Releases* bölümünden `app-release.apk` dosyasını doğrudan indir (alternatif: Actions sayfasındaki *Artifacts → NextStarIPTV-apk*).

**Yol 2 – Android Studio**: klasörü aç (Iguana/Hedgehog+, JDK 17), *Build → Build APK(s)*.

**Yol 3 – Komut satırı**: JDK 17 + Android SDK 34 ile `gradle :app:assembleRelease` (Gradle 8.5).

## Liste ekleme (kumandasız, kolay yol)
- **Ayarlar → Xtream Codes ile ekle**: sunucu, kullanıcı adı, şifre gir; M3U adresi otomatik kurulur.
- **Ayarlar → Telefondan / PC'den eklemeyi aç**: TV bir adres ve 4 haneli PIN gösterir. Aynı Wi-Fi'deki telefon/PC tarayıcısında o adresi açıp Xtream veya M3U bilgilerini klavyeyle gir. Erişim yalnızca Ayarlar ekranı açıkken çalışır, PIN her açılışta değişir, 5 yanlış denemede kilitlenir.

## TV'ye yükleme
TV'de Geliştirici seçenekleri → USB/Ağ hata ayıklamayı aç, sonra:
`adb connect <tv-ip>:5555` ve `adb install app-release.apk`
(ya da APK'yı USB bellek / "Send Files to TV" ile aktarıp dosya yöneticisinden kur).

## Kumanda
| Tuş | Menü | Oynatıcı |
|---|---|---|
| OK | seç | duraklat / devam |
| OK (uzun bas) | – | favori ekle/çıkar |
| ◀ ▶ | gezin | VOD: ±10 sn |
| ▲ ▼ | gezin | Canlı: önceki/sonraki kanal |
| CH+/CH−, ⏭/⏮ | – | kanal/bölüm değiştir |
| Geri | geri / Ana Sayfa | oynatıcıdan çık |

## Notlar
- Release APK, kolay kurulum için **debug anahtarıyla** imzalanır. Dağıtacaksan `app/build.gradle.kts` içinde kendi imza anahtarını tanımla.
- Açık metin (http) trafiğe izin verilir; IPTV akışlarının çoğu http'dir.
- Ağ DNS'i Ayarlar'dan seçilir; DoH önce denenir, olmazsa sistem DNS'ine düşer.

## Bu sürümdeki düzeltmeler
- `Root` (ana iskelet) eksikti → eklendi: sol menü, sekmeler, tam ekran oynatıcı/tarayıcı geçişi, Geri davranışı.
- `PlayerScreen.kt` v1'den kalma, projede olmayan sınıflara (`SmartDnsClient`, `SettingsManager`, `WatchlistManager`) bağlıydı → Media3 ile sıfırdan yazıldı (DoH istemcisi, öğe başlıkları, donanım/yazılım çözücü ayarı, otomatik yeniden deneme, kumanda tuşları, HA bildirimi).
- Filmler / Diziler / Radyo sekmelerinin ekranı yoktu → `BrowseScreen.kt` eklendi (grup çipleri, arama, favori, gizle).
- Tarayıcıdan yakalanan videoda WebView'in ham başlıkları (Range, Accept-Encoding) ExoPlayer'a aktarılıyordu → yalnızca User-Agent/Referer/Cookie aktarılıyor.
- Grup adı "Tümü" olan listelerde Canlı TV ekranının çökmesi önlendi.
