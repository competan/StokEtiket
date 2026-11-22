package com.erp.etiket;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private final ExecutorService is = Executors.newSingleThreadExecutor();
    private final List<Stok> stoklar = new ArrayList<>();
    private EditText eAra;
    private ProgressBar pb;
    private TextView tBilgi;
    private ArrayAdapter<Stok> adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = dp(8);

        LinearLayout kok = new LinearLayout(this);
        kok.setOrientation(LinearLayout.VERTICAL);
        kok.setPadding(pad, pad, pad, pad);

        LinearLayout satir = new LinearLayout(this);
        satir.setOrientation(LinearLayout.HORIZONTAL);
        eAra = new EditText(this);
        eAra.setHint("Stok kodu, adı veya barkod");
        eAra.setSingleLine(true);
        eAra.setInputType(InputType.TYPE_CLASS_TEXT);
        eAra.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        eAra.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH ||
                    (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN)) {
                ara();
                return true;
            }
            return false;
        });
        satir.addView(eAra, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button bAra = new Button(this);
        bAra.setText("Ara");
        bAra.setOnClickListener(v -> ara());
        satir.addView(bAra);
        kok.addView(satir);

        pb = new ProgressBar(this);
        pb.setVisibility(View.GONE);
        kok.addView(pb);

        tBilgi = new TextView(this);
        tBilgi.setPadding(dp(4), dp(4), dp(4), dp(4));
        tBilgi.setText("Aramak için stok kodu, adı ya da barkod yazın. Tümü için boş bırakıp Ara'ya basın.");
        kok.addView(tBilgi);

        ListView lv = new ListView(this);
        adapter = new ArrayAdapter<Stok>(this, android.R.layout.simple_list_item_2, android.R.id.text1, stoklar) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                View v = super.getView(pos, cv, parent);
                Stok s = getItem(pos);
                ((TextView) v.findViewById(android.R.id.text1)).setText(s.adi);
                ((TextView) v.findViewById(android.R.id.text2)).setText(String.format(new Locale("tr", "TR"),
                        "%s  •  %s  •  %s  •  Kalan: %,.2f",
                        s.stokKodu, s.birim, Stok.para(s.etiketFiyati()), s.kalan));
                return v;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p, v, pos, id) -> etiketDialog(stoklar.get(pos)));
        kok.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(kok);

        if (AyarlarActivity.prefs(this).getString("host", "").isEmpty())
            startActivity(new Intent(this, AyarlarActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, 1, 0, "Ayarlar").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            startActivity(new Intent(this, AyarlarActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void ara() {
        final String metin = eAra.getText().toString();
        pb.setVisibility(View.VISIBLE);
        tBilgi.setText("Aranıyor...");
        is.execute(() -> {
            try {
                List<Stok> sonuc = Db.ara(this, metin);
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    stoklar.clear();
                    stoklar.addAll(sonuc);
                    adapter.notifyDataSetChanged();
                    tBilgi.setText(sonuc.size() + " kayıt" + (sonuc.size() >= 300 ? " (ilk 300 gösteriliyor)" : "")
                            + ". Etiket için satıra dokunun.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pb.setVisibility(View.GONE);
                    tBilgi.setText("Hata: " + e.getMessage());
                });
            }
        });
    }

    private void etiketDialog(Stok s) {
        final SharedPreferences p = AyarlarActivity.prefs(this);
        final Bitmap bmp = EtiketYazici.ciz(s, p.getString("baslik", ""));

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(8), dp(16), 0);

        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);
        iv.setBackgroundColor(0xFFEEEEEE);
        iv.setPadding(dp(4), dp(4), dp(4), dp(4));
        l.addView(iv);

        TextView t = new TextView(this);
        t.setText("Kopya sayısı");
        t.setPadding(0, dp(8), 0, 0);
        l.addView(t);
        final EditText eKopya = new EditText(this);
        eKopya.setInputType(InputType.TYPE_CLASS_NUMBER);
        eKopya.setText("1");
        l.addView(eKopya);

        new AlertDialog.Builder(this)
                .setTitle("Etiket önizleme")
                .setView(l)
                .setPositiveButton("Yazdır", (d, w) -> {
                    int kopya = 1;
                    try { kopya = Integer.parseInt(eKopya.getText().toString().trim()); } catch (Exception ignored) {}
                    yazdir(bmp, Math.max(1, Math.min(kopya, 100)));
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private Bitmap bekleyenBmp;
    private int bekleyenKopya;

    private void yazdir(Bitmap bmp, int kopya) {
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            bekleyenBmp = bmp;
            bekleyenKopya = kopya;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 2);
            return;
        }
        final String mac = AyarlarActivity.prefs(this).getString("yaziciMac", "");
        if (mac.isEmpty()) {
            Toast.makeText(this, "Önce Ayarlar'dan yazıcı seçin.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AyarlarActivity.class));
            return;
        }
        Toast.makeText(this, "Yazdırılıyor...", Toast.LENGTH_SHORT).show();
        is.execute(() -> {
            try {
                EtiketYazici.gonder(mac, EtiketYazici.escpos(bmp, kopya));
                runOnUiThread(() -> Toast.makeText(this, "Gönderildi ✔", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setMessage("Yazdırma hatası: " + e.getMessage()
                                + "\n\nYazıcı açık ve telefonla eşleşmiş mi kontrol edin.")
                        .setPositiveButton("Tamam", null).show());
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        if (rc == 2 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED && bekleyenBmp != null) {
            yazdir(bekleyenBmp, bekleyenKopya);
            bekleyenBmp = null;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
