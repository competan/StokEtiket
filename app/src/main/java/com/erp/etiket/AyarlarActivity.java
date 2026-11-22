package com.erp.etiket;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class AyarlarActivity extends Activity {

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ayarlar", MODE_PRIVATE);
    }

    private EditText eHost, ePort, eInstance, eDb, eUser, ePass, eOnek, eBaslik;
    private TextView tYazici;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = prefs(this);

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        l.setPadding(pad, pad, pad, pad);

        baslik(l, "SQL Server (Vega)");
        eHost = alan(l, "Sunucu IP / adı (ör. 192.168.1.10)", p.getString("host", ""), InputType.TYPE_CLASS_TEXT);
        ePort = alan(l, "Port", p.getString("port", "1433"), InputType.TYPE_CLASS_NUMBER);
        eInstance = alan(l, "Instance (ör. SQLEXPRESS, boş bırakılabilir)", p.getString("instance", ""), InputType.TYPE_CLASS_TEXT);
        eDb = alan(l, "Veritabanı adı", p.getString("db", ""), InputType.TYPE_CLASS_TEXT);
        eUser = alan(l, "Kullanıcı (ör. sa)", p.getString("user", ""), InputType.TYPE_CLASS_TEXT);
        ePass = alan(l, "Şifre", p.getString("pass", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        eOnek = alan(l, "Firma tablo öneki (ör. F0101)", p.getString("onek", "F0101"), InputType.TYPE_CLASS_TEXT);

        baslik(l, "Etiket");
        eBaslik = alan(l, "Etiket üst yazısı (firma adı, boş bırakılabilir)", p.getString("baslik", ""), InputType.TYPE_CLASS_TEXT);

        baslik(l, "Yazıcı");
        tYazici = new TextView(this);
        tYazici.setText(yaziciMetni());
        tYazici.setPadding(0, dp(4), 0, dp(4));
        l.addView(tYazici);
        Button bYazici = new Button(this);
        bYazici.setText("Bluetooth yazıcı seç");
        bYazici.setOnClickListener(v -> yaziciSec());
        l.addView(bYazici);

        Button bTest = new Button(this);
        bTest.setText("Bağlantıyı test et");
        bTest.setOnClickListener(v -> { kaydet(); test(); });
        l.addView(bTest);

        Button bKaydet = new Button(this);
        bKaydet.setText("Kaydet");
        bKaydet.setOnClickListener(v -> { kaydet(); finish(); });
        l.addView(bKaydet);

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    private void kaydet() {
        prefs(this).edit()
                .putString("host", eHost.getText().toString().trim())
                .putString("port", ePort.getText().toString().trim())
                .putString("instance", eInstance.getText().toString().trim())
                .putString("db", eDb.getText().toString().trim())
                .putString("user", eUser.getText().toString().trim())
                .putString("pass", ePass.getText().toString())
                .putString("onek", eOnek.getText().toString().trim().toUpperCase())
                .putString("baslik", eBaslik.getText().toString().trim())
                .apply();
    }

    private void test() {
        Toast.makeText(this, "Bağlanılıyor...", Toast.LENGTH_SHORT).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            String sonuc;
            try {
                Db.test(this);
                sonuc = "Bağlantı başarılı ✔";
            } catch (Exception e) {
                sonuc = "Hata: " + e.getMessage();
            }
            final String s = sonuc;
            runOnUiThread(() -> new AlertDialog.Builder(this).setMessage(s)
                    .setPositiveButton("Tamam", null).show());
        });
    }

    private String yaziciMetni() {
        SharedPreferences p = prefs(this);
        String ad = p.getString("yaziciAd", "");
        return ad.isEmpty() ? "Seçili yazıcı: yok" : "Seçili yazıcı: " + ad + " (" + p.getString("yaziciMac", "") + ")";
    }

    @SuppressLint("MissingPermission")
    private void yaziciSec() {
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null || !a.isEnabled()) {
            Toast.makeText(this, "Bluetooth'u açın ve yazıcıyı telefonla eşleştirin.", Toast.LENGTH_LONG).show();
            return;
        }
        final List<BluetoothDevice> cihazlar = new ArrayList<>(a.getBondedDevices());
        if (cihazlar.isEmpty()) {
            Toast.makeText(this, "Eşleşmiş cihaz yok. Telefonun Bluetooth ayarlarından yazıcıyı eşleştirin (PIN genelde 0000 veya 1234).",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] adlar = new String[cihazlar.size()];
        for (int i = 0; i < cihazlar.size(); i++) {
            String ad = cihazlar.get(i).getName();
            adlar[i] = (ad == null ? "?" : ad) + "\n" + cihazlar.get(i).getAddress();
        }
        new AlertDialog.Builder(this).setTitle("Yazıcı seçin")
                .setItems(adlar, (d, i) -> {
                    BluetoothDevice dev = cihazlar.get(i);
                    prefs(this).edit()
                            .putString("yaziciMac", dev.getAddress())
                            .putString("yaziciAd", dev.getName() == null ? "?" : dev.getName())
                            .apply();
                    tYazici.setText(yaziciMetni());
                }).show();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) yaziciSec();
    }

    private void baslik(LinearLayout l, String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(18);
        tv.setPadding(0, dp(14), 0, dp(4));
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        l.addView(tv);
    }

    private EditText alan(LinearLayout l, String ipucu, String deger, int tip) {
        TextView tv = new TextView(this);
        tv.setText(ipucu);
        l.addView(tv);
        EditText e = new EditText(this);
        e.setText(deger);
        e.setInputType(tip);
        e.setSingleLine(true);
        l.addView(e);
        return e;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
