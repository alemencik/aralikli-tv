# Codex için ilk iş: Çalışan Android TV APK temeli

## Girdi
Bu depodaki Kotlin/Gradle Android TV uygulaması; 7 ders kartı, bunlardan 2'sinde `questions.json` ile toplam 116 soru.

## Yapılacaklar (öncelik sırasıyla)
1. Kodu ve `AGENTS.md` dosyasını okuyup mevcut durumu doğrula. Soru bankasını `python3 tools/validate_questions.py` ile kontrol et.
2. Gradle/JDK/Android SDK sürümlerini sabitle; varsa derleme hatalarını düzelt; `testDebugUnitTest` ve `assembleDebug` çalıştır. Araçlar yoksa GitHub Actions Android iş akışını doğrula. APK üretildi demeden önce çıktısını göster.
3. Android TV kumandasının UP/DOWN/OK/BACK davranışlarını, uzun metinlerde kaydırma/odak problemlerini test et; bozuk odak akışını düzelt. Örnek soru ekleme veya cevap anahtarı uydurma.
4. Doğru cevaplandıktan sonra güven seçimi Hard/Good/Easy yapıldığında yalnız bir kez kaydedildiğini, yanlış cevapta Again olduğunu test et. Çift tıklama aynı soruyu iki kere değerlendirmemeli.
5. Oturum bittiğinde ve cihaz kapatılıp açıldığında ilerlemenin korunduğunu doğrula; diğer derslerin kaydı etkilenmemeli.
6. Eksik işlevleri dürüstçe `README.md` ve bir PR özetinde yaz: `Scheduler.kt` hâlâ FSRS değil; Room ve USB import yok; tüm PDF soruları aktarılmış değil.

## Kabul kriterleri
- Derleme CI üzerinde yeşil ve debug APK indirilebilir.
- Cihazda/TV emülatöründe D-pad, OK ve Back ile dokunmadan sorular çözülebilir.
- Var olan 116 sorunun hiçbirinin soru metni, şık, doğru işareti veya ID'si izinsiz değiştirilmez.
- Soru bankası testleri geçer; belirsiz cevaplı soru otomatik puanlanmaz.
- PR içinde çalıştırılan komut ve test sonuçları belirtilir.

## Sonraki görevler, bu PR'ın parçası değil
- Room ve idempotent SharedPreferences migration.
- FSRS algoritması, sürüm sabitleme, hedef hatırlama %90, deterministik algoritma testleri.
- PDF/JSON içe aktarma + kaynak provenance + görsel soru desteği.
- Deneme sınavı, istatistik, dışa aktarma/yedekleme.
