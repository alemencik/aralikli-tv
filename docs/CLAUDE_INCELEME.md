# Claude için bağımsız kod inceleme yönergesi

İlk Codex PR'ını **yalnızca incele**, otomatik birleştirme ya da üretim dağıtımı yapma.

## Kontrol et
- Android TV özelinde D-pad odak, Back, yatay ekran, büyük metin, erişilebilirlik ve TV launcher görünürlüğü.
- Gradle, Kotlin, AGP ve Android SDK sürümlerinin gerçek derleme uyumu, CI sonucu ve APK varlığı.
- 116 sorunun kaynağa sadakati ve veri aktarma mantığında iki sütunlu PDF hataları; doğrudan doğrulanamayan her iddiayı belirt.
- Soru başına bir cevap kaydı ve yanlış cevap sonrası yeni soru kuyruğu, aynı sorunun uygunsuz anlık tekrarı.
- Uygulama veri kaybı (uygulama kapanması, sürüm yükseltme, SharedPreferences → Room geçişi).
- FSRS'nin henüz olmaması: varmış gibi yazılmasın. Kurulunca öğrenme adımları, gün gecikmesi, leech, yanlış cevap, parametre versiyonlaması ve sınav modunun takvimden ayrı tutulmasını incele.
- Hatalı/eksik/çelişkili PDF doğru cevaplarının puanlanan havuza girmemesi.
- Güvenlik: izinler, gizli bilgi, kişisel veri ve kaynak PDF'lerin yanlışlıkla repoya girmemesi.

## İnceleme raporu biçimi
1. Bloklayıcılar (dosya:satır, somut hata, yeniden üretim).
2. Önemli fakat bloklamayan bulgular.
3. Kontrol edildiği kanıtlanan testler.
4. Kontrol edilemeyen noktalar.
5. Birleştirme kararı: ONAY / DÜZELTME GEREKİYOR.

Yalnızca kanıtlanmış bulgular raporla; CI/cihaz testi yapılmadıysa açıkça söyle.
