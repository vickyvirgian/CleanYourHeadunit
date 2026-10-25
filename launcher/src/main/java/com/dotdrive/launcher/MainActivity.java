package com.dotdrive.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
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

public class MainActivity extends Activity {
    private static final int BG = Color.BLACK;
    private static final int PANEL = Color.rgb(16, 16, 16);
    private static final int BORDER = Color.rgb(38, 38, 38);
    private static final int WHITE = Color.rgb(237, 237, 237);
    private static final int MUTED = Color.rgb(140, 140, 140);
    private Typeface uiFont, labelFont, displayFont;
    private static final String OPEN_HEADUNIT = "com.andrerinas.headunitrevived";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppEntry> apps = new ArrayList<>();
    private FrameLayout screen;
    private TextView clockView;
    private TextView dateView;
    private boolean showingDrawer;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 60_000L - (System.currentTimeMillis() % 60_000L));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uiFont = loadFont("Geist-Regular", "sans-serif");
        labelFont = loadFont("GeistMono-Regular", "monospace");
        displayFont = loadFont("Doto-ROND-wght", "sans-serif-light");
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        screen = new FrameLayout(this);
        screen.setBackgroundColor(BG);
        setContentView(screen);
        refreshApps();
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshApps();
        if (showingDrawer) {
            showAppDrawer();
        } else {
            showHome();
        }
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
    }

    @Override
    protected void onPause() {
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
        List<ResolveInfo> found = getPackageManager().queryIntentActivities(
                query, 0);
        Set<String> seen = new HashSet<>();

        for (ResolveInfo info : found) {
            if (info.activityInfo == null) continue;
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(getPackageName()) || packageName.equals("com.android.launcher3")) {
                continue;
            }
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
                OPEN_HEADUNIT,
                "com.hcn.autoradio",
                "com.hcn.AutoMediaPlayer",
                "com.google.android.apps.maps",
                "com.android.chrome",
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
                {"headunit", "android auto"},
                {"radio"},
                {"music", "media", "audio"},
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
        boolean compact = getResources().getConfiguration().screenHeightDp < 420;
        showingDrawer = false;
        clockView = null;
        dateView = null;
        screen.removeAllViews();
        screen.addView(new DotField(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout base = new LinearLayout(this);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setPadding(dp(24), dp(compact ? 12 : 18), dp(24), dp(compact ? 12 : 18));
        screen.addView(base, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(new DotMark(this), new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(12), 0, 0, 0);
        TextView brandTitle = makeText("DOT DRIVE", 17, WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brandTitle.setLetterSpacing(0.10f);
        brand.addView(brandTitle);
        TextView brandSub = makeText("ANDROID HOME", 10, MUTED, Typeface.create("sans-serif", Typeface.NORMAL));
        brandSub.setLetterSpacing(0.18f);
        brand.addView(brandSub);
        header.addView(brand);

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView version = makeButton("PERANGKAT");
        version.setMinHeight(dp(48));
        version.setOnClickListener(v -> {
            Intent tools = getPackageManager().getLaunchIntentForPackage("app.cleanyour.headunit.local");
            if (tools != null) startActivity(tools);
            else startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        });
        version.setLetterSpacing(0.14f);
        header.addView(version);
        base.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 40 : 48)));

        LinearLayout body = new LinearLayout(this);
        body.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        bodyParams.topMargin = dp(compact ? 8 : 14);
        bodyParams.bottomMargin = dp(compact ? 8 : 10);
        base.addView(body, bodyParams);

        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);
        welcome.setGravity(Gravity.CENTER_VERTICAL);
        welcome.setPadding(dp(18), dp(16), dp(24), dp(16));
        welcome.setBackground(roundRect(BG, 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams welcomeParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.86f);
        welcomeParams.rightMargin = dp(16);
        body.addView(welcome, welcomeParams);

        TextView eyebrow = makeText("LAYAR UTAMA", 12, MUTED,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        eyebrow.setLetterSpacing(0.18f);
        welcome.addView(eyebrow);

        clockView = makeText("", compact ? 48 : 62, WHITE, Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockView.setIncludeFontPadding(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) clockView.setFontVariationSettings("'ROND' 100, 'wght' 400");
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clockParams.topMargin = dp(10);
        welcome.addView(clockView, clockParams);

        dateView = makeText("", 17, MUTED, Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(2);
        welcome.addView(dateView, dateParams);

        View redLine = new View(this);
        redLine.setBackgroundColor(BORDER);
        if (compact) redLine.setVisibility(View.GONE);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dp(52), dp(3));
        lineParams.topMargin = dp(24);
        lineParams.bottomMargin = dp(14);
        welcome.addView(redLine, lineParams);

        TextView welcomeCopy = makeText("Tahan kartu untuk memilih favorit.", 14, MUTED,
                Typeface.create("sans-serif", Typeface.NORMAL));
        welcome.addView(welcomeCopy);
        if (compact) welcomeCopy.setVisibility(View.GONE);

        LinearLayout favorites = new LinearLayout(this);
        favorites.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams favoritesParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1.34f);
        body.addView(favorites, favoritesParams);

        TextView favoritesLabel = makeText("FAVORIT", 11, MUTED,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        favoritesLabel.setLetterSpacing(0.20f);
        LinearLayout.LayoutParams favoriteLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(22));
        favoriteLabelParams.leftMargin = dp(4);
        favoriteLabelParams.bottomMargin = dp(7);
        favorites.addView(favoritesLabel, favoriteLabelParams);

        LinearLayout quickGrid = new LinearLayout(this);
        quickGrid.setOrientation(LinearLayout.VERTICAL);
        favorites.addView(quickGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        List<AppEntry> quickApps = getQuickApps();
        if (quickApps.isEmpty()) {
            TextView empty = makeText("Belum ada aplikasi yang bisa dibuka.", 16, MUTED,
                    Typeface.create("sans-serif", Typeface.NORMAL));
            empty.setGravity(Gravity.CENTER);
            quickGrid.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
                LinearLayout row = new LinearLayout(this);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
                if (rowIndex > 0) rowParams.topMargin = dp(10);
                quickGrid.addView(row, rowParams);

                for (int column = 0; column < 2; column++) {
                    int index = rowIndex * 2 + column;
                    LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                    if (column > 0) cellParams.leftMargin = dp(10);
                    if (index < quickApps.size()) {
                        row.addView(makeAppCard(quickApps.get(index), index), cellParams);
                    } else {
                        View blank = new View(this);
                        row.addView(blank, cellParams);
                    }
                }
            }
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        base.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView allApps = makeButton("SEMUA APLIKASI  〉");
        allApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppDrawer();
            }
        });
        footer.addView(allApps, new LinearLayout.LayoutParams(dp(220),
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView appCount = makeText(apps.size() + " APLIKASI TERSEDIA", 11, MUTED,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        appCount.setLetterSpacing(0.13f);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.leftMargin = dp(16);
        footer.addView(appCount, countParams);

        updateClock();
    }

    private void showAppDrawer() {
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
