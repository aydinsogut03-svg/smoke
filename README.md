# Sigara Sayacı (smoke)

Günde kaç sigara içtiğini saymak için basit bir Android uygulaması.

## Özellikler
- **Son sigaradan beri** canlı sayaç: gün / saat / dakika / saniye
- Sigarasız geçen süreye göre **sağlık dönüm noktaları**
- Tek dokunuşla **+1 İçtim**, yanlışlıkla basarsan **Geri Al**
- **Günlük limit**: ilerleme çubuğu, limite yaklaşınca ve aşınca uyarı
- **Fiyat**: paket fiyatı ve paketteki adet; bugünkü ve aylık harcama
- **Tasarruf**: eski alışkanlığına göre içilmeyen sigara ve biriken para
- **Ne kazandık, ne kaybettik?**: tasarruf, geri kazanılan ömür, en uzun ara / harcanan para, kaybedilen ömür, sigaraya giden zaman
- **Başarılar**: süre, para, içilmeyen sigara ve limit hedefleri; sıradaki hedefler halka grafikle
- Kısa **son 7 gün** grafiği
- Türkçe arayüz, açık/koyu tema; veriler sadece telefonunda saklanır

## İndir
[Releases](../../releases/latest) sayfasından `SigaraSayaci.apk` dosyasını indirip kur.
Android 8.0 ve üzeri gerekir.

## Derleme
Her `main` push'unda GitHub Actions APK'yı derleyip yeni bir sürüm yayınlar.
Yerelde: `./gradlew assembleRelease`
