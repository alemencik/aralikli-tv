# TV'ye özel ses efektleri

Aşağıdaki dosyalar APK'ye veya genel erişime açık GitHub deposuna eklenmez.
Yalnızca kullanma hakkına sahip olduğunuz ses kayıtlarını TV'deki uygulama klasörüne kopyalayın:

```
/sdcard/Android/data/com.daylak.araliklitv/files/milyoner_select.mp3
/sdcard/Android/data/com.daylak.araliklitv/files/milyoner_suspense.mp3
/sdcard/Android/data/com.daylak.araliklitv/files/milyoner_correct.mp3
/sdcard/Android/data/com.daylak.araliklitv/files/milyoner_wrong.mp3
```

- select: şık seçildiğinde bir kez.
- suspense: rastgele 3–5 saniyelik bekleme süresinde döngü halinde; cevap açıklandığında kesilir.
- correct: yeşil doğru cevap gösterildiğinde.
- wrong: yanlış seçilen şık kırmızıya ve doğru şık yeşile döndüğünde.

Kendi bilgisayarınızdan yüklemek için:

```bash
adb push milyoner_select.mp3 /sdcard/Android/data/com.daylak.araliklitv/files/milyoner_select.mp3
adb push milyoner_suspense.mp3 /sdcard/Android/data/com.daylak.araliklitv/files/milyoner_suspense.mp3
adb push milyoner_correct.mp3 /sdcard/Android/data/com.daylak.araliklitv/files/milyoner_correct.mp3
adb push milyoner_wrong.mp3 /sdcard/Android/data/com.daylak.araliklitv/files/milyoner_wrong.mp3
```

Bunlar yoksa uygulama yerleşik özgün tonları kullanır.
