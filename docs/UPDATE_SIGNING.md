# Android TV — otomatik APK güncelleme

## Çalışma mantığı
- Uygulama açılınca herkese açık GitHub Releases listesini internetten kontrol eder.
- Daha yeni sürüm bulursa ana menüde **Güncelle** düğmesi çıkar.
- Düğmeye basınca APK indirilir ve GitHub'ın SHA-256 özetiyle doğrulanır.
- Android, yüklemeyi kullanıcıya onaylatır. Kullanıcı izni olmadan sessiz güncelleme yapılmaz.
- Aynı imzayla ve artan `versionCode` ile güncellenirse ders ilerlemesi korunur.

## Bir defalık imza kurulumu (ASUS Ubuntu)
Bu depodaki `tools/setup-signing.sh` dosyasını ASUS'ta çalıştır:

```bash
sudo apt install gh
gh auth login
bash tools/setup-signing.sh
```

Betik ASUS'ta özel imza anahtarı oluşturur; herkese açık GitHub deposuna KESİNLİKLE koymaz. Anahtar ve şifre `~/.config/araliklitv/` altında özel dosyalarda saklanır, GitHub'a şifreli **Actions Secrets** olarak aktarılır. Repo değişkeni `ANDROID_SIGNING_ENABLED=true` olur, GitHub Actions v0.2.0 sürümünü aynı kalıcı anahtarla derler.

Yeni sürümlerde `app/build.gradle.kts` içindeki `versionCode` ve `versionName` artırılmalı; `.github/workflows/publish-apk.yml` release etiketi de aynı sürüme güncellenmeli. Release yayını için aynı imza anahtarı kullanılmalı.

**Önemli:** Önceki GitHub Actions test APK'ları geçici *debug* anahtarıyla imzalanmıştır. Yeni sabit imza, daha önce kurulmuş APK'yla uyuşmayabilir. Bu durumda Android mevcut uygulamanın üzerine güncelleme kuramaz. Kaldırıp yeniden kurmak uygulama verilerini siler; önce kaydı yedekle. İlk kez sabit imzalı sürüm kurulduktan sonra sonraki otomatik güncellemeler aynı imzayla sorunsuz çalışabilir.

## Güvenlik
- GitHub repo genel erişime açık olabilir; private keystore ve şifre hiçbir zaman commit edilmez.
- SHA-256 doğrulaması ve Android imza kontrolü birlikte kullanılır.
- APK güncellemesi sunucudan doğrudan kurulmaz, Android'in kurulum onayı gereklidir.
