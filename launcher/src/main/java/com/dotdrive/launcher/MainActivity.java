package com.dotdrive.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.*;

public class MainActivity extends Activity {
    private static int BG = Color.BLACK;
    private static int PANEL = Color.rgb(16, 16, 16);
    private static int BORDER = Color.rgb(38, 38, 38);
    private static int WHITE = Color.rgb(237, 237, 237);
    private static int MUTED = Color.rgb(140, 140, 140);
    private Typeface uiFont, labelFont, displayFont;
    private static final String OPEN_HEADUNIT = "com.andrerinas.headunitrevived";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppEntry> apps = new ArrayList<>();
    private FrameLayout screen;
    private TextView clockView;
    private TextView dateView;
    private boolean showingDrawer;
    private DashboardView dashboard;
    private MediaBridge media;
    private WeatherStore weather;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean weatherBusy;
    private boolean visible;
    private String weatherError;
    private Bitmap wallpaper;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            if (!showingDrawer && dashboard != null) {
                dashboard.refreshClock(); dashboard.updateMedia(media);
                if (System.currentTimeMillis() % 60_000L < 1000L) refreshWeather(false);
            }
            handler.postDelayed(this, showingDrawer ? 60_000L : 1000L - (System.currentTimeMillis() % 1000L));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uiFont = loadFont("Geist-Regular", "sans-serif");
        labelFont = loadFont("GeistMono-Regular", "monospace");
        displayFont = loadFont("Doto-ROND-wght", "sans-serif-light");
        weather = new WeatherStore(this);
        media = new MediaBridge(this, handler, () -> { if (dashboard != null && !showingDrawer) dashboard.updateMedia(media); });
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        screen = new FrameLayout(this);
        screen.setBackgroundColor(BG);
        setContentView(screen);
        refreshApps();
        showHome();
        File saved = new File(getNoBackupFilesDir(), "wallpaper.jpg");
        if (saved.isFile()) io.execute(() -> {
            Bitmap loaded = BitmapFactory.decodeFile(saved.getAbsolutePath());
            handler.post(() -> { if (!isDestroyed()) {wallpaper=loaded;if(visible && !showingDrawer)showHome();} });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        refreshApps();
        if (showingDrawer) {
            showAppDrawer();
        } else {
            showHome();
        }
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        media.start();
    }

    @Override
    protected void onPause() {
        visible = false;
        media.stop();
        handler.removeCallbacks(clockTick);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refreshApps();
        showHome();
    }

    @Override
    public void onBackPressed() {
        if (showingDrawer) {
            showHome();
        } else {
            moveTaskToBack(true);
        }
    }

    private void refreshApps() {
        apps.clear();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>(getPackageManager().queryIntentActivities(query, 0));
        Intent television = new Intent(Intent.ACTION_MAIN);
        television.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        found.addAll(getPackageManager().queryIntentActivities(television, 0));
        Set<String> seen = new HashSet<>();

        for (ResolveInfo info : found) {
            if (info.activityInfo == null || !info.activityInfo.enabled || !info.activityInfo.applicationInfo.enabled) continue;
            String packageName = info.activityInfo.packageName;
            String key = packageName + "/" + info.activityInfo.name;
            if (!seen.add(key)) continue;

            CharSequence label = info.loadLabel(getPackageManager());
            Drawable icon = info.loadIcon(getPackageManager());
            apps.add(new AppEntry(
                    label == null ? packageName : label.toString(),
                    packageName,
                    info.activityInfo.name,
                    icon));
        }

        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
    }

    private List<AppEntry> getQuickApps() {
        List<AppEntry> quick = new ArrayList<>();
        String[] packageOrder = {
                "com.hcn.autoradio",
                "com.hcn.AutoMediaPlayer",
                "com.mediatek.filemanager",
                "com.google.android.apps.maps",
                "com.hcn.autosetting",
                "com.android.settings"
        };

        for (String packageName : packageOrder) {
            AppEntry match = findPackage(packageName);
            if (match != null && !containsPackage(quick, match.packageName)) {
                quick.add(match);
            }
            if (quick.size() == 4) return applyFavorites(quick);
        }

        String[][] labelHints = {
                {"radio"},
                {"music", "media", "audio"},
                {"file", "berkas"},
                {"maps", "navigation"},
                {"settings"}
        };
        for (String[] hints : labelHints) {
            AppEntry match = findLabel(hints);
            if (match != null && !containsPackage(quick, match.packageName)) {
                quick.add(match);
            }
            if (quick.size() == 4) return applyFavorites(quick);
        }

        for (AppEntry app : apps) {
            if (!containsPackage(quick, app.packageName)) {
                quick.add(app);
            }
            if (quick.size() == 4) break;
        }
        return applyFavorites(quick);
    }

    private List<AppEntry> applyFavorites(List<AppEntry> base) {
        List<String> baseline = new ArrayList<>();
        List<String> saved = new ArrayList<>();
        Set<String> installed = new HashSet<>();
        for (AppEntry app : base) baseline.add(app.packageName);
        for (AppEntry app : apps) installed.add(app.packageName);
        for (int i = 0; i < 4; i++) saved.add(getSharedPreferences("favorites", MODE_PRIVATE).getString("slot" + i, ""));
        List<AppEntry> result = new ArrayList<>();
        for (String pkg : FavoriteSlots.apply(baseline, saved, installed)) {
            AppEntry app = findPackage(pkg);
            if (app != null) result.add(app);
        }
        return result;
    }

    private AppEntry findPackage(String packageName) {
        for (AppEntry app : apps) {
            if (app.packageName.equalsIgnoreCase(packageName)) return app;
        }
        return null;
    }

    private AppEntry findLabel(String[] hints) {
        for (AppEntry app : apps) {
            String label = app.label.toLowerCase(Locale.ROOT);
            for (String hint : hints) {
                if (label.contains(hint)) return app;
            }
        }
        return null;
    }

    private boolean containsPackage(List<AppEntry> list, String packageName) {
        for (AppEntry app : list) {
            if (app.packageName.equalsIgnoreCase(packageName)) return true;
        }
        return false;
    }

    private void showHome() {
        showingDrawer = false;
        applyTheme();
        screen.removeAllViews();
        List<DashboardView.Tile> tiles = new ArrayList<>();
        for (AppEntry app : getQuickApps()) tiles.add(new DashboardView.Tile(app.label, app.icon));
        dashboard = new DashboardView(this, getPreferences(0).getInt("theme", 0), wallpaper, uiFont, labelFont, displayFont,
            new DashboardView.Actions() {
                public void theme(int mode) {
                    if (mode == 2 && wallpaper == null) { chooseWallpaper(); return; }
                    getPreferences(0).edit().putInt("theme", mode).apply(); showHome();
                }
                public void wallpaper() { chooseWallpaper(); }
                public void shortcut(String kind) { openShortcut(kind); }
                public void weather() { weatherMenu(); }
                public void drawer() { showAppDrawer(); }
                public void favorite(int slot) { List<AppEntry> quick=getQuickApps(); if(slot<quick.size())launchApp(quick.get(slot));else chooseFavorite(slot); }
                public void chooseFavorite(int slot) { MainActivity.this.chooseFavorite(slot); }
                public void mediaAccess() { requestMediaAccess(); }
                public void mediaControl(int which) { media.control(which); dashboard.updateMedia(media); }
                public void chooseAudio() { AppEntry active=media.current()==null?null:findPackage(media.current().getPackageName());if(active!=null)launchApp(active);else openAudio(); }
            }, tiles, apps.size());
        screen.addView(dashboard, new FrameLayout.LayoutParams(-1, -1));
        dashboard.updateMedia(media); dashboard.updateWeather(weather, weatherError);
        refreshWeather(false);
    }

    private void showAppDrawer() {
        dashboard = null;
        showingDrawer = true;
        clockView = null;
        dateView = null;
        screen.removeAllViews();

        LinearLayout base = new LinearLayout(this);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setPadding(dp(24), dp(18), dp(24), dp(18));
        screen.addView(base, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = makeButton("‹");
        back.setTextSize(30);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(56), dp(48)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHome();
            }
        });

        TextView title = makeText("SEMUA APLIKASI", 16, WHITE,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(14);
        header.addView(title, titleParams);

        TextView count = makeText(String.valueOf(apps.size()), 13, MUTED,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(count);
        base.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(12);
        base.addView(scroll, scrollParams);

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (apps.isEmpty()) {
            TextView empty = makeText("Tidak ada aplikasi yang bisa dibuka.", 16, MUTED,
                    Typeface.create("sans-serif", Typeface.NORMAL));
            empty.setGravity(Gravity.CENTER);
            rows.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));
            return;
        }

        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int columns = Math.max(4, Math.min(6, widthDp / 150));
        int rowCount = (apps.size() + columns - 1) / columns;

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(112));
            rowParams.bottomMargin = dp(8);
            rows.addView(row, rowParams);

            for (int column = 0; column < columns; column++) {
                int index = rowIndex * columns + column;
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (column > 0) cellParams.leftMargin = dp(8);
                if (index < apps.size()) {
                    row.addView(makeDrawerCard(apps.get(index)), cellParams);
                } else {
                    View blank = new View(this);
                    row.addView(blank, cellParams);
                }
            }
        }
    }

    private View makeAppCard(final AppEntry app, final int slot) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(14), dp(16), dp(14));
        card.setBackground(roundRect(PANEL, dp(8), Color.TRANSPARENT));
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription("Buka " + app.label);
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(roundRect(hasFocus ? WHITE : PANEL, dp(8), Color.TRANSPARENT));
                invertLabels(v, hasFocus ? BG : WHITE);
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(app);
            }
        });
        card.setOnLongClickListener(v -> {
            String[] labels = new String[apps.size()];
            for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;
            new android.app.AlertDialog.Builder(this).setTitle("Pilih favorit " + (slot + 1))
                .setItems(labels, (dialog, which) -> {
                    android.content.SharedPreferences prefs = getSharedPreferences("favorites", MODE_PRIVATE);
                    android.content.SharedPreferences.Editor edit = prefs.edit();
                    String chosen = apps.get(which).packageName;
                    for (int i = 0; i < 4; i++) if (i != slot && chosen.equals(prefs.getString("slot" + i, ""))) edit.remove("slot" + i);
                    edit.putString("slot" + slot, chosen).apply();
                    showHome();
                }).setNegativeButton("Batal", null).show();
            return true;
        });

        LinearLayout iconLine = new LinearLayout(this);
        iconLine.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = appIcon(app.icon, 38);
        iconLine.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView arrow = makeText("↗", 17, MUTED, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        arrowParams.leftMargin = dp(12);
        iconLine.addView(arrow, arrowParams);
        card.addView(iconLine);

        TextView label = makeText(app.label, 16, WHITE,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(10);
        card.addView(label, labelParams);
        return card;
    }

    private View makeDrawerCard(final AppEntry app) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(6), dp(8), dp(6), dp(8));
        card.setBackground(roundRect(PANEL, dp(8), Color.TRANSPARENT));
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription("Buka " + app.label);
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(roundRect(hasFocus ? WHITE : PANEL, dp(8), Color.TRANSPARENT));
                invertLabels(v, hasFocus ? BG : WHITE);
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(app);
            }
        });

        card.addView(appIcon(app.icon, 42), new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView label = makeText(app.label, 13, WHITE,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(8);
        card.addView(label, labelParams);
        return card;
    }

    private ImageView appIcon(Drawable drawable, int sizeDp) {
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (drawable != null) {
            icon.setImageDrawable(drawable.mutate());
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0f);
            icon.setColorFilter(new ColorMatrixColorFilter(matrix));
        }
        return icon;
    }

    private void launchApp(AppEntry app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(app.packageName, app.activityName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Tidak bisa membuka " + app.label,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private TextView makeButton(String text) {
        TextView button = makeText(text, 14, WHITE,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        button.setBackground(roundRect(WHITE, dp(6), Color.TRANSPARENT));
        button.setTextColor(BG);
        button.setFocusable(true);
        button.setClickable(true);
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(roundRect(hasFocus ? BG : WHITE, dp(6), hasFocus ? WHITE : Color.TRANSPARENT));
                ((TextView) v).setTextColor(hasFocus ? WHITE : BG);
            }
        });
        return button;
    }

    private TextView makeText(String text, int sizeSp, int color, Typeface typeface) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(sizeSp >= 40 || text.equals("DOT DRIVE") ? displayFont : sizeSp <= 12 ? labelFont : uiFont);
        return view;
    }

    private void applyTheme() {
        boolean day = getPreferences(0).getInt("theme",0)==1;
        BG=day?0xffeeeeef:0xff08080a;PANEL=day?0xfffafafa:0xff171719;BORDER=day?0xffd9dadd:0xff414147;WHITE=day?0xff1c1c1c:0xffededed;MUTED=day?0xff626469:0xffa4a5aa;
        screen.setBackgroundColor(BG);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        int flags=getWindow().getDecorView().getSystemUiVisibility();
        getWindow().getDecorView().setSystemUiVisibility(day?flags|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR:flags&~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void chooseFavorite(int slot) {
        String[] labels=new String[apps.size()];for(int i=0;i<apps.size();i++)labels[i]=apps.get(i).label;
        new android.app.AlertDialog.Builder(this).setTitle("Pilih favorit "+(slot+1)).setItems(labels,(dialog,which)->{
            android.content.SharedPreferences prefs=getSharedPreferences("favorites",MODE_PRIVATE);
            android.content.SharedPreferences.Editor edit=prefs.edit();String chosen=apps.get(which).packageName;
            for(int i=0;i<4;i++)if(i!=slot&&chosen.equals(prefs.getString("slot"+i,"")))edit.remove("slot"+i);
            edit.putString("slot"+slot,chosen).apply();showHome();
        }).setNegativeButton("Batal",null).show();
    }

    private void note(String message) { if (!isDestroyed()) new android.app.AlertDialog.Builder(this).setMessage(message).setPositiveButton("Oke",null).show(); }
    private void openIntent(Intent intent) {try{startActivity(intent);}catch(Exception e){note("Halaman ini tidak tersedia pada perangkat.");}}
    private void requestMediaAccess() {
        new android.app.AlertDialog.Builder(this).setTitle("Hubungkan kontrol musik")
            .setMessage("Aktifkan akses notifikasi DOT DRIVE agar Android mengizinkan pembacaan judul lagu dan kontrol pemutar. Launcher tidak membaca atau menyimpan isi pesan/notifikasi lain. Beberapa aplikasi radio dan Bluetooth vendor tidak menyediakan sesi media.")
            .setNegativeButton("Nanti",null).setPositiveButton("Buka pengaturan",(d,w)->openIntent(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))).show();
    }

    private void openShortcut(String kind) {
        if(kind.equals("wifi")){openIntent(new Intent(Settings.ACTION_WIFI_SETTINGS));return;}
        if(kind.equals("bluetooth")){openIntent(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));return;}
        if(kind.equals("tools")){
            new android.app.AlertDialog.Builder(this).setTitle("Pengaturan DOT DRIVE").setItems(new String[]{"Pengaturan Android","Clean Your Headunit","Izin kontrol musik","Ganti wallpaper","Atur cuaca","Pilih pemutar musik"},(d,i)->{
                if(i==0)openIntent(new Intent(Settings.ACTION_SETTINGS));else if(i==1){Intent app=getPackageManager().getLaunchIntentForPackage("app.cleanyour.headunit.local");if(app==null)note("Clean Your Headunit belum terpasang.");else openIntent(app);}else if(i==2)requestMediaAccess();else if(i==3)chooseWallpaper();else if(i==4)weatherMenu();else chooseAudioApp();
            }).show();return;
        }
        String[] pkgs,hints;
        switch(kind){
            case "radio":pkgs=new String[]{"com.hcn.autoradio"};hints=new String[]{"radio"};break;
            case "btmusic":pkgs=new String[]{"com.autochips.bluetooth","com.mediatek.bluetooth.dtt"};hints=new String[]{"bt music","bluetooth music","musik bluetooth"};break;
            case "files":pkgs=new String[]{"com.mediatek.filemanager","com.android.documentsui"};hints=new String[]{"file manager","files","berkas","explorer"};break;
            case "maps":pkgs=new String[]{"com.google.android.apps.maps","com.waze","net.osmand.plus","net.osmand"};hints=new String[]{"maps","navigation","navigasi","waze"};break;
            case "auto":pkgs=new String[]{OPEN_HEADUNIT,"gb.xxy.hr"};hints=new String[]{"open headunit","headunit reloaded","android auto"};break;
            default:openAudio();return;
        }
        AppEntry app=null;for(String pkg:pkgs){app=findPackage(pkg);if(app!=null)break;}if(app==null)app=findLabel(hints);
        if(app==null)note("Aplikasi untuk pintasan ini belum tersedia atau sedang dibekukan. Semua aplikasi aktif tetap bisa dibuka dari daftar aplikasi.");else launchApp(app);
    }

    private void openAudio() {
        String saved=getPreferences(0).getString("audio","");AppEntry chosen=findPackage(saved);
        if(chosen!=null){launchApp(chosen);return;}
        chosen=findPackage("com.hcn.AutoMediaPlayer");if(chosen!=null){launchApp(chosen);return;}
        chooseAudioApp();
    }
    private void chooseAudioApp() {
        List<AppEntry> candidates=new ArrayList<>();for(AppEntry app:apps){String n=app.label.toLowerCase(Locale.ROOT);if(n.contains("music")||n.contains("musik")||n.contains("audio")||n.contains("spotify")||n.contains("radio")||n.contains("poweramp")||n.contains("vlc"))candidates.add(app);}
        if(candidates.isEmpty())candidates.addAll(apps);
        final List<AppEntry> choices=candidates;String[] names=new String[choices.size()];for(int i=0;i<names.length;i++)names[i]=choices.get(i).label;
        new android.app.AlertDialog.Builder(this).setTitle("Pilih sumber musik").setItems(names,(d,i)->{getPreferences(0).edit().putString("audio",choices.get(i).packageName).apply();launchApp(choices.get(i));}).setNegativeButton("Batal",null).show();
    }

    private void refreshWeather(boolean force) {
        if(weatherBusy||!weather.configured()||(!force&&!weather.due()))return;
        weatherBusy=true;io.execute(()->{String error=null;try{weather.refresh();}catch(Exception e){error="Offline / cuaca belum tersedia";}final String result=error;
            handler.post(()->{weatherBusy=false;weatherError=result;if(!isDestroyed()&&dashboard!=null&&!showingDrawer)dashboard.updateWeather(weather,result);});});
    }

    private void weatherMenu() {
        new android.app.AlertDialog.Builder(this).setTitle(weather.city()).setItems(new String[]{"Pilih kota","Perbarui cuaca","Tentang data cuaca"},(d,i)->{
            if(i==0)chooseCity();else if(i==1){if(weather.configured())refreshWeather(true);else chooseCity();}else note("Cuaca dari Open-Meteo. Nama kota dikirim saat pencarian, koordinat kota pilihan dikirim saat pembaruan. Tidak memakai GPS. Data terakhir disimpan lokal; waktu pembaruan/cache ditampilkan. open-meteo.com");
        }).show();
    }
    private void chooseCity() {
        android.widget.EditText field=new android.widget.EditText(this);field.setSingleLine(true);field.setHint("Contoh: Banjarnegara");field.setPadding(dp(20),dp(16),dp(20),dp(16));
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Kota untuk cuaca").setMessage("Pilih kota secara manual. Tidak meminta lokasi GPS.").setView(field).setNegativeButton("Batal",null).setPositiveButton("Cari",null).create();
        dialog.setOnShowListener(v->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{String query=field.getText().toString().trim();if(query.length()<2){field.setError("Isi nama kota");return;}button.setEnabled(false);
            io.execute(()->{try{JSONArray list=weather.search(query);handler.post(()->{if(isDestroyed()||!dialog.isShowing())return;button.setEnabled(true);if(list==null||list.length()==0){field.setError("Kota tidak ditemukan");return;}String[] labels=new String[list.length()];for(int i=0;i<labels.length;i++){JSONObject p=list.optJSONObject(i);labels[i]=p.optString("name")+", "+p.optString("admin1")+", "+p.optString("country");}dialog.dismiss();new android.app.AlertDialog.Builder(this).setTitle("Pilih kota").setItems(labels,(d,index)->{try{weather.choose(list.getJSONObject(index));weatherError=null;showHome();refreshWeather(true);}catch(Exception e){note("Kota tidak dapat dipilih.");}}).setNegativeButton("Batal",null).show();});}
            catch(Exception e){handler.post(()->{if(!isDestroyed()&&dialog.isShowing()){button.setEnabled(true);field.setError("Pencarian gagal. Periksa koneksi internet.");}});}});
        }));dialog.show();
    }

    private void chooseWallpaper() {Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("image/*");try{startActivityForResult(pick,91);}catch(Exception e){note("Pemilih gambar belum tersedia. Aktifkan File Manager/Documents terlebih dahulu.");}}
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request!=91||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();io.execute(()->{try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
            if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException();BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
            while(bounds.outWidth/options.inSampleSize>2048||bounds.outHeight/options.inSampleSize>2048)options.inSampleSize*=2;
            Bitmap decoded;try(InputStream in=getContentResolver().openInputStream(uri)){decoded=BitmapFactory.decodeStream(in,null,options);}if(decoded==null)throw new IOException();
            float scale=Math.min(1f,1280f/Math.max(decoded.getWidth(),decoded.getHeight()));Bitmap image=scale<1?Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(decoded.getWidth()*scale)),Math.max(1,Math.round(decoded.getHeight()*scale)),true):decoded;
            try(OutputStream out=new FileOutputStream(new File(getNoBackupFilesDir(),"wallpaper.jpg"))){if(!image.compress(Bitmap.CompressFormat.JPEG,85,out))throw new IOException();}
            handler.post(()->{if(!isDestroyed()){wallpaper=image;getPreferences(0).edit().putInt("theme",2).apply();if(visible)showHome();}});
        }catch(Exception e){handler.post(()->{if(!isDestroyed())note("Gambar gagal dibuka. Coba JPG/PNG dengan ukuran lebih kecil.");});}});
    }
    @Override protected void onDestroy(){handler.removeCallbacks(clockTick);media.stop();io.shutdownNow();super.onDestroy();}

    private GradientDrawable roundRect(int color, int radiusDp, int borderColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp);
        if (borderColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), borderColor);
        }
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateClock() {
        if (clockView == null || dateView == null) return;
        Date now = new Date();
        clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        dateView.setText(new SimpleDateFormat("EEEE, d MMMM", new Locale("id", "ID")).format(now));
    }

    private static class AppEntry {
        final String label;
        final String packageName;
        final String activityName;
        final Drawable icon;

        AppEntry(String label, String packageName, String activityName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.activityName = activityName;
            this.icon = icon;
        }
    }

    private static class DotMark extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DotMark(Activity activity) {
            super(activity);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cellW = getWidth() / 3f;
            float cellH = getHeight() / 3f;
            float radius = Math.min(cellW, cellH) * 0.14f;
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    if (row == 2 && column == 0) continue;
                    paint.setColor(WHITE);
                    float x = (column + 0.5f) * cellW;
                    float y = (row + 0.5f) * cellH;
                    canvas.drawCircle(x, y, radius, paint);
                }
            }
        }
    }

    private Typeface loadFont(String name, String fallback) {
        try { return Typeface.createFromAsset(getAssets(), "fonts/" + name + ".ttf"); }
        catch (Exception e) { return Typeface.create(fallback, Typeface.NORMAL); }
    }

    private void invertLabels(View view, int color) {
        if (view instanceof TextView) ((TextView) view).setTextColor(color);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) invertLabels(group.getChildAt(i), color);
        }
    }

    private static class DotField extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        DotField(Activity activity) { super(activity); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            float density = getResources().getDisplayMetrics().density;
            paint.setColor(Color.argb(100, 255, 255, 255));
            for (float y = 60 * density; y < getHeight(); y += 120 * density)
                for (float x = 60 * density; x < getWidth(); x += 120 * density)
                    canvas.drawCircle(x, y, 1.3f * density, paint);
        }
    }
}
