package com.erp.etiket;

import android.content.Context;
import android.content.SharedPreferences;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Vega SQL Server veritabanından stok okuma (jTDS). */
public class Db {

    private static Connection baglan(Context c) throws Exception {
        SharedPreferences p = AyarlarActivity.prefs(c);
        String host = p.getString("host", "").trim();
        String port = p.getString("port", "1433").trim();
        String instance = p.getString("instance", "").trim();
        String db = p.getString("db", "").trim();
        String user = p.getString("user", "").trim();
        String pass = p.getString("pass", "");

        if (host.isEmpty() || db.isEmpty())
            throw new Exception("Önce Ayarlar'dan sunucu ve veritabanı bilgilerini girin.");

        Class.forName("net.sourceforge.jtds.jdbc.Driver");
        StringBuilder url = new StringBuilder("jdbc:jtds:sqlserver://")
                .append(host).append(":").append(port.isEmpty() ? "1433" : port)
                .append("/").append(db)
                .append(";charset=Cp1254;loginTimeout=10;socketTimeout=30");
        if (!instance.isEmpty()) url.append(";instance=").append(instance);

        DriverManager.setLoginTimeout(10);
        return DriverManager.getConnection(url.toString(), user, pass);
    }

    /** Tablo öneki (ör. F0101). SQL enjeksiyonuna karşı sadece harf/rakam/_ kabul edilir. */
    private static String onek(Context c) throws Exception {
        String o = AyarlarActivity.prefs(c).getString("onek", "F0101").trim();
        if (!o.matches("[A-Za-z0-9_]+"))
            throw new Exception("Tablo öneki geçersiz: " + o);
        return o;
    }

    public static List<Stok> ara(Context c, String metin) throws Exception {
        String o = onek(c);
        String sql =
                "SELECT TOP 300 s.IND, s.STOKKODU, s.MALINCINSI, ISNULL(s.KDV,0) AS KDV, " +
                "       ISNULL(s.KALAN,0) AS KALAN, b.BIRIMADI, b.BARCODE, " +
                "       ISNULL(b.SATISFIYATI1,0) AS FIYAT, ISNULL(b.KDVDAHIL,0) AS KDVDAHIL " +
                "FROM " + o + "TBLSTOKLAR s " +
                "LEFT JOIN " + o + "TBLBIRIMLEREX b ON b.STOKNO = s.IND " +
                "WHERE ISNULL(s.STATUS,0) <> 2 " +
                "  AND (s.STOKKODU LIKE ? OR s.MALINCINSI LIKE ? OR b.BARCODE = ?) " +
                "ORDER BY s.STOKKODU";

        List<Stok> liste = new ArrayList<>();
        try (Connection con = baglan(c);
             PreparedStatement ps = con.prepareStatement(sql)) {
            String like = "%" + metin.trim() + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, metin.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Stok s = new Stok();
                    s.ind = rs.getInt("IND");
                    s.stokKodu = bos(rs.getString("STOKKODU"));
                    s.adi = bos(rs.getString("MALINCINSI"));
                    s.kdv = rs.getDouble("KDV");
                    s.kalan = rs.getDouble("KALAN");
                    s.birim = bos(rs.getString("BIRIMADI"));
                    s.barkod = bos(rs.getString("BARCODE"));
                    s.fiyat = rs.getDouble("FIYAT");
                    s.kdvDahil = rs.getInt("KDVDAHIL") != 0;
                    liste.add(s);
                }
            }
        }
        return liste;
    }

    public static void test(Context c) throws Exception {
        String o = onek(c);
        try (Connection con = baglan(c);
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM " + o + "TBLSTOKLAR");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
        }
    }

    private static String bos(String s) {
        return s == null ? "" : s.trim();
    }
}
