package com.erp.etiket;

import java.util.Locale;

public class Stok {
    public int ind;
    public String stokKodu = "";
    public String adi = "";
    public String birim = "";
    public String barkod = "";
    public double fiyat;      // SATISFIYATI1
    public boolean kdvDahil;  // TBLBIRIMLEREX.KDVDAHIL
    public double kdv;        // TBLSTOKLAR.KDV (%)
    public double kalan;

    /** Etikette gösterilecek KDV dahil fiyat. */
    public double etiketFiyati() {
        if (kdvDahil) return fiyat;
        return fiyat * (1 + kdv / 100.0);
    }

    public static String para(double d) {
        return String.format(new Locale("tr", "TR"), "%,.2f TL", d);
    }

    @Override
    public String toString() {
        return stokKodu + " - " + adi;
    }
}
