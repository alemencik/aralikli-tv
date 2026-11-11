# Aralıklı TV — Android TV sınav ve tekrar uygulaması

Kumandayla çıkmış soruları çözmek için Kotlin ile geliştirilmiş, **çevrimdışı soru çözebilen** Android TV projesi. Soru bankası ve cevaplama geçmişi cihazda tutulur. İnternet yalnızca isteğe bağlı ders videoları ve sürüm kontrolü için gerekir.

## Özellikler
- Ders seçimi, 5 şıklı soru çözme, doğru/yanlış açıklaması ve öğrenme geçmişi.
- **Tahmin ettim / Zorlandım / Biliyordum / Çok kolaydı** değerlendirmeleri.
- Tahmin ve yanlış cevapların erken tekrarı, sonrasında gün bazlı tekrar aralıkları.
- Mavi-mor neon **özgün yarışma stüdyosu arka planı**. Herhangi bir yarışma programının orijinal grafik/müzik görüntüsü kullanılmaz.
- Çevrimdışı özgün soru ambiyansı, cevap bekleme ve doğru/yanlış sesleri, başarı animasyonu ve ses açma/kapatma.
- **Video Dersler** menüsü: AUZEF resmi materyalleri, AKSİS ve doğrulanmış Anadolu Üniversitesi Atatürk İlkeleri II dersi bağlantısı. TV tarayıcısı/YouTube kurulu değilse açılmayabilir.
- Uygulama açılışında GitHub Releases üzerinden yeni sürüm denetimi; kullanıcının onayıyla APK indirme ve Android kurulum ekranına geçme. İndirilen dosya **SHA-256** ile doğrulanır.

## İlk kurulum ve güncelleme için önemli
Android'in bir APK güncellemesini kabul etmesi için **aynı uygulama kimliği, aynı imza sertifikası ve artan sürüm kodu** gerekir. Önceden GitHub Actions ile üretilmiş geçici test APK'ları imzaları değişebildiği için güncellenemeyebilir.

**Kalıcı imza anahtarı henüz GitHub Secrets'a kurulmadıysa herkese açık imzalı sürüm yayımlanmaz.** Yalnızca test APK'sı Actions → Artifacts bölümünden alınabilir. Tek seferlik kurulum ve güvenli anahtar yedekleme talimatı: [docs/UPDATE_SIGNING.md](docs/UPDATE_SIGNING.md). ASUS Ubuntu bilgisayarında `bash tools/setup-signing.sh` komutu gerekli Secrets'ları oluşturup sürüm yayınına başlayacak.

Kurulumdan sonra yeni sürüm bulunduğunda uygulama “Güncelle” düğmesini gösterir. Android'in kurulum izinlerini kullanıcının onaylaması gerekir, arka planda sessiz kurulum yapılmaz.

## Soru bankası ve veri doğruluğu
`app/src/main/assets/questions.json`: **389 puanlanabilir soru**. 116 ilk soru (Rapor 38 / Yerel 78) otomatik kaynak-cevap metni eşleşmesiyle, 195 İngilizce soru PDF'nin açık `Cevap:` satırlarıyla, 20 Atatürk II soru PDF'de kalın yazılmış doğru şıklarla, 58 Türk Dili I soru PDF'de kırmızı/kalın işaretlenmiş doğru şıklarla aktarılmıştır. Tüm sorular insan tarafından tek tek akademik açıdan denetlenmiş değildir. Cevabı belirsiz veya şıkları eksik olan sorular eklenmemiştir. Eski eleme raporu ilk 116 soruya aittir: `tools/import_report.json`.

Öğrenme geçmişi `SharedPreferences` üzerinde tutulur. Uygulamayı kaldırmak verileri silebilir; güncelleme yaparken uygulamayı kaldırmadan yüklemek gerekir.

## Teknik durum
- Android Gradle Plugin 8.9.2, Kotlin 2.1.20, JDK 17, compileSdk 35, minSdk 23.
- `gradle testDebugUnitTest` ve `gradle assembleDebug` komutları GitHub Actions ile doğrulanır. [Actions](https://github.com/alemencik/aralikli-tv/actions).
- Tekrar planlayıcısı şu an **FSRS değildir**: `Scheduler.kt` içinde açıklanmış MVP kuralları kullanılır.
- Room, Compose for TV, deneme sınavı, genişletilmiş PDF içe aktarma, USB yedeği ve çoklu kullanıcı profili henüz uygulanmadı.

## Geliştirme
Codex için [AGENTS.md](AGENTS.md), [docs/CODEX_GOREV.md](docs/CODEX_GOREV.md); bağımsız kod incelemesi için [docs/CLAUDE_INCELEME.md](docs/CLAUDE_INCELEME.md) bulunur.

Kaynak PDF dosyalarını herkese açık GitHub deposuna eklemeyin; paylaşım haklarını kontrol edin.
