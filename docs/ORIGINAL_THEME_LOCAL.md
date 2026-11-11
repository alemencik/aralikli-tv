# Yarışma arka planı

Varsayılan arka plan, Raphael'in **Atina Okulu** freskinin kullanıcı tarafından sağlanan görselidir. **Ayarlar → Yarışma arka planı** üzerinden Atina Okulu, neon veya TV'ye aktarılmış özel görsel seçilebilir.

Özel bir görsel kullanmak için TV'de uygulamayı bir kez açın. Ardından ASUS Ubuntu'da:

```bash
adb push ~/milyoner_stage.jpg /sdcard/Android/data/com.daylak.araliklitv/files/milyoner_stage.jpg
adb shell am force-stop com.daylak.araliklitv
adb shell am start -n com.daylak.araliklitv/.MainActivity
```

PNG için iki tarafta da `milyoner_stage.png` adını kullanın. Görsel TV'de saklanır. 16:9 görseller ekranı doldurur. Diğer görseller merkezden kırpılır ve soru metninin okunabilirliği için karartılır.
