package app.cleanyour.headunit.helper;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.graphics.Color;
import android.widget.*;
import rikka.shizuku.Shizuku;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() { public void run() { refresh(); handler.postDelayed(this, 2000); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24),dp(20),dp(24),dp(20)); body.setBackgroundColor(Color.rgb(21,24,30));
        ScrollView scroll = new ScrollView(this); scroll.addView(body); setContentView(scroll);
        TextView title = text("CLEAN YOUR HEADUNIT\nSiapkan head unit", 28); body.addView(title);
        body.addView(text("1. Pasang Shizuku di head unit.\n2. Jalankan Shizuku.\n3. Berikan izin ke Headunit Helper.\n4. Hubungkan aplikasi di HP ke IP dan port ADB head unit.", 18));
        status = text("Memeriksa Shizuku…", 18); body.addView(status);
        button(body,"Pasang / buka Shizuku", () -> {
            Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"));
            try { startActivity(i); } catch(Exception e) { Toast.makeText(this,"Buka shizuku.rikka.app/download dari HP untuk mengunduh APK.",Toast.LENGTH_LONG).show(); }
        });
        button(body,"Berikan izin Shizuku", () -> {
            try { if(Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(10); else Toast.makeText(this,"Jalankan Shizuku dahulu.",Toast.LENGTH_LONG).show(); }
            catch(Exception e) { Toast.makeText(this,"Buka Shizuku dan jalankan layanannya dahulu.",Toast.LENGTH_LONG).show(); }
        });
        body.addView(text("Android 11+: mulai melalui Wireless debugging di Shizuku. Android 8–10: aktivasi awal memerlukan ADB yang sudah tersedia atau root. Shizuku aktif tidak berarti port ADB Wi-Fi sudah terbuka. Versi awal aplikasi HP memakai ADB TCP (contoh port 8555), bukan port pairing TLS.",16));
    }
    private TextView text(String s,int size) { TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,12,0,12);return t; }
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density); }
    private void button(LinearLayout b,String s,Runnable r) { Button t=new Button(this); t.setText(s); t.setMinHeight(dp(56));t.setOnClickListener(v->r.run());b.addView(t); }
    private void refresh() {
        Bundle b=StatusProvider.status(this); StringBuilder ips=new StringBuilder();
        try { for(NetworkInterface n: Collections.list(NetworkInterface.getNetworkInterfaces())) for(InetAddress a:Collections.list(n.getInetAddresses())) if(!a.isLoopbackAddress() && a instanceof Inet4Address) ips.append(a.getHostAddress()).append("  "); } catch(Exception ignored) {}
        status.setText("Shizuku terpasang: "+b.getBoolean("installed")+"\nShizuku berjalan: "+b.getBoolean("running")+"\nIzin Helper: "+b.getBoolean("granted")+"\nIP head unit: "+ips+"\n"+(b.getBoolean("ready")?"Siap diperiksa dari HP.":"Selesaikan langkah di atas."));
    }
    @Override protected void onResume() {super.onResume();handler.post(poll);}
    @Override protected void onPause() {handler.removeCallbacks(poll);super.onPause();}
}
