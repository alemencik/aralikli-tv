# Kendi televizyonunda orijinal yarışma görüntüsü ve seslerini kullanma

Bu mod yalnızca **kullanıcının kendi cihazına kopyaladığı dosyaları** açar.
Uygulamanın herkese açık kaynaklarında veya GitHub APK'sında TV programına ait resim/müzik **bulunmaz**.

## Kullanılacak dosyalar

Android TV uygulamasının kendi klasöründe:
- `milyoner_stage.jpg` veya `milyoner_stage.png`: televizyonun gerçek yarışma sahnesinden aldığınız görsel.
- `milyoner_correct.mp3`: isteğe bağlı doğru cevap sesi.
- `milyoner_wrong.mp3`: isteğe bağlı yanlış cevap sesi.
- `milyoner_warning.mp3`: isteğe bağlı son saniyeler sesi.

Dosyalar eksikse uygulama kendi neon sahnesi ve cihaz seslerine döner.
Kişisel kullanımda bile hak sahibi şartlarını gözetin; uygulamaya üçüncü taraf görselleri yüklemiyoruz.

## ASUS Ubuntu + ADB ile yükleme

Önce uygulamayı televizyonda en az bir kez çalıştır. ASUS'ta fotoğrafı 
`~/milyoner_stage.jpg` olarak kaydet. Sonra:

```bash
adb devices
adb shell mkdir -p /sdcard/Android/data/com.daylak.araliklitv/files
adb push ~/milyoner_stage.jpg /sdcard/Android/data/com.daylak.araliklitv/files/milyoner_stage.jpg
adb shell am force-stop com.daylak.araliklitv
adb shell am start -n com.daylak.araliklitv/.MainActivity
```

MP3 dosyaları varsa aynı şekilde aktar:
```bash
adb push ~/milyoner_correct.mp3 /sdcard/Android/data/com.daylak.araliklitv/files/milyoner_correct.mp3
adb push ~/milyoner_wrong.mp3 /sdcard/Android/data/com.daylak.araliklitv/files/milyoner_wrong.mp3
adb push ~/milyoner_warning.mp3 /sdcard/Android/data/com.daylak.araliklitv/files/milyoner_warning.mp3
```

Sesler uygulamanın `Ses efektleri` ayarı açıkken çalışır.
Ana menüden `Yarışma arka planı` düğmesiyle orijinal fotoğraf ve özgün neon sahnesi arasında geçiş yapabilirsin.
Görseli değiştirdiğinde uygulamayı yeniden başlat. APK imzası değişmemişse uygulama verileri korunur.

## Notlar

- Görsel 16:9, tercihen 1920×1080 olmalı. Uygulama merkezden kırpar ve okunabilirlik için hafifçe karartır.
- Geçici test APK'sını mevcut uygulamanın üstüne kurarken imza uyuşmazlığı yaşanabilir. Uygulamayı kaldırmak çalışma geçmişini silebilir.
- GitHub deponun özel olması **telif lisansı sağlamaz**. Bu yöntem orijinal dosyayı GitHub'a eklemeden cihaz içinde kullanır.
