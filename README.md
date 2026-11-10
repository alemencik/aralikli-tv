# Aralıklı TV: Android TV çıkmış sorular çalışma uygulaması

**Bu arşiv Android Studio kaynak projesidir; APK değildir.** Android Studio'da açıp `app` modülünü derleyerek APK üretilebilir. Projenin bu ortamda Android SDK ile derlenmesi mümkün olmadığından çalışır APK doğrulaması henüz yapılmadı.

## Mevcut özellikler

- Kumandadan ders seçimi, 5 şıklı soruyu OK ile cevaplama, doğru/yanlış gösterimi.
- Doğru cevapta **Zorlandım / Biliyordum / Çok kolaydı** değerlendirmesi.
- Yanlış soruyu 5 başka cevap sonra veya 5 dakika sonra tekrar gündeme getiren, sonrasında gün bazlı uzatan **ilk sürüm aralıklı tekrar planlayıcısı**.
- Android `SharedPreferences` ile uygulama kapanınca kaybolmayan öğrenme geçmişi.
- Günlük tekrar, ders bazında çalışma ve basit istatistikler.
- Cevap anahtarı bilinmeyen derslerin soru sayısını yanlışlıkla aktifmiş gibi göstermeyen boş ders kartları.

> **FSRS henüz uygulanmadı.** `Scheduler.kt` şeffaf, sabit kurallı MVP planlayıcısıdır. FSRS entegrasyonu için mevcut soruların kimlikleri korunmalıdır. Henüz deneme sınavı, JSON'u TV üstünden import etme, USB yedeği, çoklu kullanıcı profili veya Compose for TV arayüzü yoktur.

## Soruların kaynağı

`app/src/main/assets/questions.json` yüklenen PDF'lerden programatik olarak üretilmiştir. İki PDF'de cevap anahtarı açıkça bulunan sorular içinden, soru + A–E şıkları + seçilen cevabın metni birlikte otomatik metin eşleşmesiyle kabul edilenler kullanılmıştır; 116 sorunun hepsi insan tarafından sayfa sayfa doğrulanmamıştır. Seçilen cevaplar bağımsız olarak tarihsel/bilimsel doğruluk yönünden araştırılmamış, **kaynak PDF'nin cevap anahtarı** esas alınmıştır.

Aktarıma ilişkin dosya bazlı özet ve çıkarılan hatalı/çelişkili adaylar `import_report.json` içindedir. Çelişkili cevaplar çalışma havuzundan çıkarıldı. Taranmış PDF'ler ve doğrulanamayan cevap anahtarları henüz kullanılmıyor.

## GitHub ve Codex ile geliştirme

Bu kaynak paketinde `.github/workflows/android.yml`, `AGENTS.md`, `docs/CODEX_GOREV.md`, `docs/CLAUDE_INCELEME.md`, `docs/VERI_DURUMU.md` bulunur. GitHub Actions her push sonrası soru bankasını kontrol eder, Gradle testlerini çalıştırır ve başarılı olursa **debug APK** dosyasını *Actions → Artifacts* altında paylaşır. Bu iş akışı henüz çalıştırılıp doğrulanmadı.

Yeni **gizli** GitHub deposu açmak için GitHub CLI ve Git kurulu, `gh auth login` tamamlanmış bilgisayarda proje klasörünün içinden `bash tools/github_kur.sh` çalıştırılabilir. Betik mevcut depoların üzerine yazmaz. Alternatif olarak `https://github.com/new` adresinden `aralikli-tv` adlı **Private** depo oluşturup bu projenin dosyalarını yükleyin. Ardından Codex'te bu depoyu seçip `docs/CODEX_GOREV.md` görevini verin. Claude'a yalnızca Codex PR bağlantısı ile `docs/CLAUDE_INCELEME.md` üzerinden bağımsız inceleme yaptırın.

PDF kaynak dosyalarını GitHub deposuna koymayın; gerekiyorsa Codex'e ayrıca, sadece yetkili erişimde sağlayın.

## Android Studio ile açma

1. Android Studio'yu yükleyip bu klasörü proje olarak açın.
2. Gradle senkronizasyonunu tamamlayın (Android Gradle Plugin 8.9.2, Kotlin 2.1.20, JDK 17, compileSdk 35).
3. Bir Android TV emülatörü veya gerçek Android TV cihazı seçin.
4. Run ile çalıştırın veya Build > Build APK(s) ile APK oluşturun.

## Soruları tekrar işlemek

Arşivdeki `tools/ingest_pdfs.py` betiği `PyMuPDF` gerektirir. PDF dosyalarıyla aynı klasörde kaynak soruları okuyup `questions.json` ve `import_report.json` üretir. Betik makineye özgü sabit dizin kullanmaz. `python3 tools/ingest_pdfs.py --pdf-dir /PDF/DOSYALARININ/YOLU` komutuyla PDF klasörünü belirtin. **Dikkat:** Bu komut mevcut `questions.json` dosyasını yeniden üretir; önce dosyaları ve soru kimliklerini yedekleyin. Mevcut sürüm yalnızca iki belirli kaynağı otomatik işleyebilir.

## Geliştirme sırası

1. Diğer PDF'ler için metin/görsel denetimi ve doğru cevap anahtarı eşleştirmesi.
2. Soru bankasını uygulamaya USB/JSON ile dışarıdan içe aktarma.
3. FSRS algoritmasının sürüm ve testlerle entegre edilmesi.
4. TV odak/okunabilirlik testleri, istatistik, yedek, deneme sınavı.
