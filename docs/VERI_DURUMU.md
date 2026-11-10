# PDF veri aktarımı — gerçek durum

16 yükleme, aynı PDF'nin 2 birebir kopya çifti, toplam 14 farklı PDF.

`app/src/main/assets/questions.json`: 7 ders kartı, toplam 116 puanlanabilir soru:

| Ders | JSON soru | Durum |
|---|---:|---|
| Rapor Yazma ve Mülakat Teknikleri | 38 | Kaynaktaki işaretli cevap metni ile seçilen şık eşleşmesi otomatik denetlenmiş |
| Yerel Yönetimler ve Sosyal Hizmet | 78 | Aynı otomatik eşleşme denetimi |
| Atatürk İlkeleri ve İnkılap Tarihi I | 0 | Henüz aktarılmadı |
| Atatürk İlkeleri ve İnkılap Tarihi II | 0 | Henüz aktarılmadı |
| Türk Dili I | 0 | Henüz aktarılmadı |
| Yabancı Dil I | 0 | Henüz aktarılmadı |
| Sosyal Hizmet Kuramları | 0 | Henüz aktarılmadı |

**Not:** 116 soru otomatik işlenmiştir, hepsi insan tarafından sayfa sayfa kontrol edilmiş değildir. `tools/import_report.json` ayrıntılı eleme ve çakışma kayıtları içerir. Sayfa görseli/elle doğrulama gerektiren soruları **tahmin etme**. PDF'ler güvenlik, boyut ve lisans nedenleriyle repoya dahil değildir; ayrı sağlanmalıdır.

Soru kimliklerini (`id`) değiştirirsen öğrenme geçmişi bozulur. Cevap anahtarı hatalı/çelişkili bir soruyu düzelteceksen doğrulama raporu ve içerik sürümleme planı yap.
