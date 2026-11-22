# Stok Etiket (Android)

Vega ERP'deki stokları arar, 56 mm termal Bluetooth yazıcıya etiket basar.

## APK'yı elde etme
**A) Android Studio:** Klasörü aç → Build › Build APK(s) → `app/build/outputs/apk/debug/app-debug.apk`

**B) GitHub (Android Studio'suz):** Klasörü bir GitHub deposuna yükle → Actions sekmesi →
"APK derle" bitince *StokEtiket-apk* dosyasını indir.

## Telefonda ilk ayar
1. Yazıcıyı telefonun Bluetooth ayarlarından eşleştir (PIN genelde 0000 / 1234).
2. Uygulama → Ayarlar:
   - Sunucu IP, port (1433), instance (varsa), veritabanı, kullanıcı, şifre — Vega'nın config'indekilerle aynı
   - Tablo öneki: F0101 (firma 01 için; Vega tablolarındaki `F0xxxTBLSTOKLAR` başı)
   - Yazıcıyı seç → "Bağlantıyı test et" → Kaydet
3. Telefon, SQL Server ile aynı ağda (Wi-Fi) olmalı.

## Sunucu tarafı
- SQL Server Configuration Manager → TCP/IP etkin, port 1433 (veya sabit port).
- Windows Güvenlik Duvarı: 1433 TCP gelen izni (instance kullanılıyorsa UDP 1434 de).
- SQL Server "Force Encryption" açıksa jTDS bağlanamaz.

## Etiket içeriği
Üst yazı (isteğe bağlı) · Mal adı · Stok kodu / birim · KDV dahil fiyat · Barkod (EAN-13/EAN-8/Code128).
Etiket resim olarak basılır, Türkçe karakter sorunu olmaz.

Veriler: TBLSTOKLAR (STATUS≠2) + TBLBIRIMLEREX (BIRIMADI, BARCODE, SATISFIYATI1, KDVDAHIL).
KDVDAHIL=0 ise fiyat, stok kartındaki KDV oranı eklenerek gösterilir.
