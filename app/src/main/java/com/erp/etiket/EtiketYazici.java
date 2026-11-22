package com.erp.etiket;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 56/58 mm termal yazıcı için etiket çizer ve ESC/POS raster olarak gönderir.
 * Etiket resim olarak basıldığı için Türkçe karakter (kod sayfası) sorunu yaşanmaz.
 */
public class EtiketYazici {

    /** 58 mm yazıcıda basılabilir genişlik: 48 mm = 384 nokta (203 dpi). */
    public static final int GENISLIK = 384;
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // ---------- Etiket çizimi ----------

    public static Bitmap ciz(Stok s, String baslik) {
        Paint pBaslik = paint(22, false);
        Paint pAd = paint(30, true);
        Paint pKod = paint(22, false);
        Paint pFiyat = paint(46, true);
        Paint pKucuk = paint(20, false);

        List<String> adSatirlari = sar(s.adi, pAd, GENISLIK - 8, 3);
        Bitmap barkod = barkodCiz(s.barkod, GENISLIK - 24, 80);

        int y = 0;
        int h = 6;
        if (baslik != null && !baslik.isEmpty()) h += 28;
        h += adSatirlari.size() * 36 + 30 + 58;
        if (barkod != null) h += barkod.getHeight() + 28;
        h += 10;

        Bitmap bmp = Bitmap.createBitmap(GENISLIK, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);
        y = 6;

        if (baslik != null && !baslik.isEmpty()) {
            ortala(c, baslik, pBaslik, y + 20);
            y += 28;
        }
        for (String satir : adSatirlari) {
            ortala(c, satir, pAd, y + 28);
            y += 36;
        }
        String kodSatiri = s.stokKodu + (s.birim.isEmpty() ? "" : "   /  " + s.birim);
        ortala(c, kodSatiri, pKod, y + 22);
        y += 30;

        ortala(c, Stok.para(s.etiketFiyati()), pFiyat, y + 46);
        y += 58;

        if (barkod != null) {
            c.drawBitmap(barkod, (GENISLIK - barkod.getWidth()) / 2f, y, null);
            y += barkod.getHeight();
            ortala(c, s.barkod, pKucuk, y + 22);
        }
        return bmp;
    }

    private static Paint paint(float size, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        p.setTextSize(size);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        return p;
    }

    private static void ortala(Canvas c, String t, Paint p, float y) {
        float w = p.measureText(t);
        if (w > GENISLIK) {   // sığmazsa küçült
            Paint k = new Paint(p);
            k.setTextSize(p.getTextSize() * GENISLIK / w * 0.98f);
            p = k;
            w = p.measureText(t);
        }
        c.drawText(t, (GENISLIK - w) / 2f, y, p);
    }

    /** Metni kelime kelime satırlara böler. */
    private static List<String> sar(String metin, Paint p, int max, int maxSatir) {
        List<String> sonuc = new ArrayList<>();
        StringBuilder satir = new StringBuilder();
        for (String kelime : metin.split("\\s+")) {
            String dene = satir.length() == 0 ? kelime : satir + " " + kelime;
            if (p.measureText(dene) <= max || satir.length() == 0) {
                satir.setLength(0);
                satir.append(dene);
            } else {
                sonuc.add(satir.toString());
                satir.setLength(0);
                satir.append(kelime);
            }
        }
        if (satir.length() > 0) sonuc.add(satir.toString());
        if (sonuc.size() > maxSatir) sonuc = new ArrayList<>(sonuc.subList(0, maxSatir));
        if (sonuc.isEmpty()) sonuc.add("");
        return sonuc;
    }

    private static Bitmap barkodCiz(String kod, int w, int h) {
        if (kod == null || kod.isEmpty()) return null;
        try {
            BarcodeFormat f = kod.matches("\\d{13}") ? BarcodeFormat.EAN_13
                    : kod.matches("\\d{8}") ? BarcodeFormat.EAN_8
                    : BarcodeFormat.CODE_128;
            BitMatrix m = new MultiFormatWriter().encode(kod, f, w, h);
            Bitmap b = Bitmap.createBitmap(m.getWidth(), m.getHeight(), Bitmap.Config.ARGB_8888);
            for (int x = 0; x < m.getWidth(); x++)
                for (int y = 0; y < m.getHeight(); y++)
                    b.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
            return b;
        } catch (Exception e) {
            return null; // geçersiz barkod: barkodsuz bas
        }
    }

    // ---------- ESC/POS ----------

    public static byte[] escpos(Bitmap bmp, int kopya) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int satirBayt = (w + 7) / 8;
        byte[] raster = new byte[satirBayt * h];
        int[] px = new int[w];
        for (int y = 0; y < h; y++) {
            bmp.getPixels(px, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                int c = px[x];
                int gri = (((c >> 16) & 0xff) * 30 + ((c >> 8) & 0xff) * 59 + (c & 0xff) * 11) / 100;
                if (gri < 128) raster[y * satirBayt + x / 8] |= (byte) (0x80 >> (x % 8));
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x1B); out.write(0x40);               // ESC @  (sıfırla)
        for (int k = 0; k < Math.max(1, kopya); k++) {
            out.write(0x1D); out.write(0x76); out.write(0x30); out.write(0x00); // GS v 0
            out.write(satirBayt & 0xff); out.write((satirBayt >> 8) & 0xff);
            out.write(h & 0xff); out.write((h >> 8) & 0xff);
            out.write(raster, 0, raster.length);
            out.write(0x1B); out.write(0x64); out.write(4); // ESC d 4 (4 satır besle)
        }
        return out.toByteArray();
    }

    // ---------- Bluetooth ----------

    @SuppressLint("MissingPermission")
    public static void gonder(String mac, byte[] veri) throws Exception {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) throw new Exception("Bu cihazda Bluetooth yok.");
        if (!a.isEnabled()) throw new Exception("Bluetooth kapalı.");
        BluetoothDevice d = a.getRemoteDevice(mac);
        a.cancelDiscovery();

        BluetoothSocket sock = null;
        try {
            sock = d.createRfcommSocketToServiceRecord(SPP);
            try {
                sock.connect();
            } catch (Exception ilk) {
                // Bazı ucuz yazıcılarda güvensiz soket gerekir
                try { sock.close(); } catch (Exception ignored) {}
                sock = d.createInsecureRfcommSocketToServiceRecord(SPP);
                sock.connect();
            }
            OutputStream os = sock.getOutputStream();
            int parca = 512;
            for (int i = 0; i < veri.length; i += parca) {
                os.write(veri, i, Math.min(parca, veri.length - i));
                os.flush();
                Thread.sleep(15);   // yazıcı tamponu taşmasın
            }
            Thread.sleep(800);      // son baytlar basılmadan soket kapanmasın
        } finally {
            if (sock != null) try { sock.close(); } catch (Exception ignored) {}
        }
    }
}
