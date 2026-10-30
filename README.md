# NextStar IPTV (v2)

Android TV / Google TV için sıfırdan yazılmış IPTV ve medya oynatıcı. Kotlin, Jetpack Compose, Media3 (ExoPlayer), OkHttp (DoH), Coil.

## APK nasıl alınır

**Yol 1 – GitHub Actions (önerilen, bilgisayarda kurulum gerekmez)**
1. Bu klasörün içeriğini yeni bir GitHub deposuna yükle (`main` dalı).
2. Depoda *Actions → Build APK → Run workflow* (push de otomatik tetikler).
3. İş bitince sayfanın altındaki *Artifacts → NextStarIPTV-apk* dosyasını indir, zip'ten `app-release.apk` çıkar.

**Yol 2 – Android Studio**: klasörü aç (Iguana/Hedgehog+, JDK 17), *Build → Build APK(s)*.

**Yol 3 – Komut satırı**: JDK 17 + Android SDK 34 ile `gradle :app:assembleRelease` (Gradle 8.5).

## TV'ye yükleme
TV'de Geliştirici seçenekleri → USB/Ağ hata ayıklamayı aç, sonra:
`adb connect <tv-ip>:5555` ve `adb install app-release.apk`
(ya da APK'yı USB bellek / "Send Files to TV" ile aktarıp dosya yöneticisinden kur).

## Yenilikler (v2.1)
- **Radyo:** kompakt liste (varsayılan) veya küçük kartlı ızgara; çıkınca listede kaldığın satıra döner. Çalarken ICY bilgisi (sanatçı – şarkı, istasyon adı) gösterilir; bu, internet radyosunun RDS karşılığıdır. Arka plan Ayarlar'dan seçilir: koyu gradyan, radyo logosu, görsel veya video (adres ya da dosya).
- **Film/Dizi:** ▼ tuşu (veya Menü/Altyazı tuşu) ile ses ve altyazı seçimi; seçtiğin dil sonraki videolar için hatırlanır. Ayarlar'da varsayılan dil de seçilebilir.
- **Tarayıcı:** favoriler. Sayfadayken kumandadaki Menü tuşu sayfayı favoriye ekler; adres yazıp "Favori ekle" ya da son açılanlardan yıldız da kullanılabilir.
- **Çökme kaydı:** uygulama çökerse yığın izi kaydedilir, sonraki açılışta Ana Sayfa ve Ayarlar'da görünür.
- **Bellek:** büyük listelerde RAM kullanımı belirgin azaldı, `largeHeap` açıldı.

## Kumanda
| Tuş | Menü | Oynatıcı |
|---|---|---|
| OK | seç | duraklat / devam |
| OK (uzun bas) | – | favori ekle/çıkar |
| ▼ (film/dizi) | gezin | ses ve altyazı menüsü |
| Menü | tarayıcıda: favori ekle | ses/altyazı menüsü (canlı TV'de) |
| ◀ ▶ | gezin | VOD: ±10 sn |
| ▲ ▼ | gezin | Canlı: önceki/sonraki kanal |
| CH+/CH−, ⏭/⏮ | – | kanal/bölüm değiştir |
| Geri | geri / Ana Sayfa | oynatıcıdan çık |

## Notlar
- Release APK, kolay kurulum için **debug anahtarıyla** imzalanır. Dağıtacaksan `app/build.gradle.kts` içinde kendi imza anahtarını tanımla.
- Açık metin (http) trafiğe izin verilir; IPTV akışlarının çoğu http'dir.
- Ağ DNS'i Ayarlar'dan seçilir; DoH önce denenir, olmazsa sistem DNS'ine düşer.
