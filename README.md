# Sigara Sayacı (smoke)

Günde kaç sigara içtiğini saymak için basit bir Android uygulaması.

## Özellikler
- Tek dokunuşla **+1 Sigara İçtim**, yanlış basarsan **Geri Al**
- **Günlük limit**: ilerleme çubuğu, kalan sayı ve limit aşınca uyarı
- **Son sigaradan beri** geçen süre
- Haftalık ortalama, bu ayın toplamı, paket fiyatı girilirse bugünkü harcama
- **Son 30 gün** geçmişi, çubuk grafikle
- Türkçe arayüz, açık/koyu tema; veriler sadece telefonunda saklanır

## İndir
[Releases](../../releases/latest) sayfasından `SigaraSayaci.apk` dosyasını indirip kur.
Android 8.0 ve üzeri gerekir.

## Derleme
Her `main` push'unda GitHub Actions APK'yı derleyip yeni bir sürüm yayınlar.
Yerelde: `./gradlew assembleRelease`
