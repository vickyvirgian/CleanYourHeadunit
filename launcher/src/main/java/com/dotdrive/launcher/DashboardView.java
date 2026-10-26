package com.dotdrive.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.media.MediaMetadata;
import android.media.session.*;
import android.media.session.MediaController;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

final class DashboardView extends FrameLayout {
    interface Actions {
        void theme(int mode);void wallpaper();void shortcut(String kind);void weather();void drawer();
        void favorite(int slot);void chooseFavorite(int slot);void mediaAccess();void mediaControl(int key);void chooseAudio();
    }
    static final class Tile {
        final String label;final Drawable icon;
        Tile(String label,Drawable icon){this.label=label;this.icon=icon;}
    }
    private final Activity activity;
    private final Actions actions;
    private final Typeface ui,mono,dot;
    private final int ink,muted,surface,line,base,accent=0xffd71921;
    private final boolean light;
    private final int width;
    private TextView clock,date,seconds,connection,weatherCity,temperature,condition,weatherDetail,forecast,weatherAge;
    private TextView track,artist,source,mediaTime,badge;
    private TextView previous,play,next;
    private ProgressBar progress;
    private long lastNetwork;
    private ImageView cover;
    private Glyph weatherGlyph;
    private Bitmap lastCover;
    private String lastPackage="",lastSource="PEMUTAR MUSIK";
    private Boolean wasPlaying;
    DashboardView(Activity a,int mode,Bitmap wallpaper,Typeface ui,Typeface mono,Typeface dot,Actions actions,List<Tile> tiles,int appCount){
        super(a);activity=a;this.ui=ui;this.mono=mono;this.dot=dot;this.actions=actions;
        light=mode==1;ink=light?0xff1c1c1c:0xffededed;muted=light?0xff626469:0xffa4a5aa;
        surface=light?0xfffafafa:mode==2?0xed141417:0xff171719;line=light?0xffd9dadd:0xff414147;base=light?0xffeeeeef:0xff08080a;
        width=a.getResources().getConfiguration().screenWidthDp;
        setBackgroundColor(base);
        if(mode==2 && wallpaper!=null){ImageView image=new ImageView(a);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(wallpaper);addView(image,new FrameLayout.LayoutParams(-1,-1));View scrim=new View(a);scrim.setBackgroundColor(0x38000000);addView(scrim,new FrameLayout.LayoutParams(-1,-1));}
        LinearLayout content=new LinearLayout(a);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(12),dp(16),dp(12));
        if(a.getResources().getConfiguration().screenHeightDp<480){ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.addView(content,new ScrollView.LayoutParams(-1,dp(480)));addView(scroll,new FrameLayout.LayoutParams(-1,-1));}
        else addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=row();
        TextView brand=text("● DOT DRIVE",17,dot);brand.setContentDescription("DOT DRIVE, layar utama");header.addView(brand,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout modes=row();String[] names={"GELAP","TERANG","TRANSPARAN"};
        for(int i=0;i<3;i++){final int choice=i;TextView b=button(names[i],()->actions.theme(choice));b.setTextSize(11);b.setTypeface(mono);b.setPadding(dp(10),0,dp(10),0);if(i==2)b.setOnLongClickListener(v->{actions.wallpaper();return true;});if(i==mode){b.setBackground(shape(ink,line,24));b.setTextColor(base);}modes.addView(b,new LinearLayout.LayoutParams(-2,dp(44)));}
        modes.setBackground(shape(surface,line,24));header.addView(modes);
        badge=text("KONTROL MUSIK",11,mono);badge.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);badge.setOnClickListener(v->actions.mediaAccess());badge.setContentDescription("Izin kontrol musik");
        if(width>=800)header.addView(badge,new LinearLayout.LayoutParams(0,-1,1));
        content.addView(header,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout top=row();top.setGravity(Gravity.FILL);LinearLayout.LayoutParams topParams=new LinearLayout.LayoutParams(-1,0,2.2f);topParams.topMargin=dp(12);content.addView(top,topParams);
        LinearLayout timeCard=card();timeCard.setGravity(Gravity.CENTER_VERTICAL);addWeight(top,timeCard,1.05f,false);
        LinearLayout clockHead=row();TextView local=text("LOKAL",11,mono);clockHead.addView(local,new LinearLayout.LayoutParams(0,-2,1));seconds=text("DETIK —",10,mono);clockHead.addView(seconds);timeCard.addView(clockHead);
        clock=text("",width<800?40:62,dot);clock.setIncludeFontPadding(false);if(Build.VERSION.SDK_INT>=26)clock.setFontVariationSettings("'ROND' 100, 'wght' 450");timeCard.addView(clock,new LinearLayout.LayoutParams(-1,-2));
        date=text("",width<800?12:14,mono);timeCard.addView(date);timeCard.addView(new View(a),new LinearLayout.LayoutParams(-1,0,1));divider(timeCard);connection=text("Memeriksa jaringan",11,mono);timeCard.addView(connection);
        LinearLayout weather=card();addWeight(top,weather,1.02f,true);weather.setOnClickListener(v->actions.weather());weather.setContentDescription("Cuaca, ketuk untuk mengatur kota");weather.setFocusable(true);
        weatherCity=text("CUACA LOKAL",11,mono);weather.addView(weatherCity);
        LinearLayout tempRow=row();temperature=text("—°",width<800?36:48,dot);tempRow.addView(temperature,new LinearLayout.LayoutParams(0,-1,1));weatherGlyph=new Glyph(a);tempRow.addView(weatherGlyph,new LinearLayout.LayoutParams(dp(width<800?34:54),-1));weather.addView(tempRow,new LinearLayout.LayoutParams(-1,dp(width<800?48:64)));
        condition=text("Atur kota cuaca",15,ui);weather.addView(condition);weatherDetail=text("Tanpa akses GPS otomatis",11,mono);weather.addView(weatherDetail);weather.addView(new View(a),new LinearLayout.LayoutParams(-1,0,1));divider(weather);forecast=text("Ketuk kartu untuk memilih kota",11,mono);forecast.setMaxLines(2);weather.addView(forecast);weatherAge=text("Open-Meteo",10,mono);weather.addView(weatherAge);
        LinearLayout music=card();addWeight(top,music,1.02f,true);
        source=text("PEMUTAR MUSIK",11,mono);music.addView(source);track=text("Musik belum diputar",width<800?16:20,ui);track.setMaxLines(2);track.setEllipsize(TextUtils.TruncateAt.END);music.addView(track,new LinearLayout.LayoutParams(-1,0,1));
        artist=text("Buka sumber audio",13,ui);artist.setMaxLines(1);artist.setEllipsize(TextUtils.TruncateAt.END);music.addView(artist);artist.setOnClickListener(v->actions.chooseAudio());track.setOnClickListener(v->actions.chooseAudio());
        progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgressTintList(ColorStateList.valueOf(ink));progress.setProgressBackgroundTintList(ColorStateList.valueOf(line));music.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        mediaTime=text("—:— / —:—",10,mono);music.addView(mediaTime);
        LinearLayout controls=row();previous=button("‹‹",()->actions.mediaControl(-1));play=button("▶",()->actions.mediaControl(0));next=button("››",()->actions.mediaControl(1));
        previous.setContentDescription("Lagu sebelumnya");play.setContentDescription("Putar atau jeda");next.setContentDescription("Lagu berikutnya");addWeight(controls,previous,1,false);addWeight(controls,play,1,true);addWeight(controls,next,1,true);music.addView(controls,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout rail=new LinearLayout(a);rail.setOrientation(LinearLayout.VERTICAL);rail.setGravity(Gravity.CENTER);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(48),-1);rp.leftMargin=dp(10);top.addView(rail,rp);
        for(String key:new String[]{"BT","WI-FI","TOOLS"}){String type=key.equals("BT")?"bluetooth":key.equals("WI-FI")?"wifi":"tools";TextView b=button(key,()->actions.shortcut(type));b.setTextSize(key.length()>3?10:12);b.setPadding(0,0,0,0);b.setBackground(shape(surface,line,24));b.setContentDescription("Buka "+type);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(46),dp(46));bp.topMargin=dp(4);bp.bottomMargin=dp(4);rail.addView(b,bp);}
        LinearLayout middle=row();LinearLayout.LayoutParams mid=new LinearLayout.LayoutParams(-1,0,1.05f);mid.topMargin=dp(12);content.addView(middle,mid);
        LinearLayout shortcuts=row();addWeight(middle,shortcuts,.9f,false);
        String[] shortcutNames={"RADIO","BLUETOOTH","BERKAS"};String[] shortcutTypes={"radio","btmusic","files"};String[] symbols={"◉","ᛒ","▱"};
        for(int i=0;i<3;i++){final String type=shortcutTypes[i];LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);TextView circle=button(symbols[i],()->actions.shortcut(type));circle.setTextSize(24);circle.setPadding(0,0,0,0);circle.setBackground(shape(surface,line,28));box.addView(circle,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView label=text(shortcutNames[i],width<800?9:11,mono);label.setGravity(Gravity.CENTER);label.setOnClickListener(v->actions.shortcut(type));box.addView(label);addWeight(shortcuts,box,1,i>0);}
        FrameLayout banner=new FrameLayout(a);banner.setBackground(shape(surface,line,16));addWeight(middle,banner,1.9f,true);
        cover=new ImageView(a);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setAlpha(.28f);banner.addView(cover,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bannerBody=new LinearLayout(a);bannerBody.setPadding(dp(14),dp(8),dp(14),dp(8));bannerBody.setOrientation(LinearLayout.VERTICAL);banner.addView(bannerBody,new FrameLayout.LayoutParams(-1,-1));
        TextView eyebrow=text("PUTAR LANGSUNG DARI HEAD UNIT",10,mono);bannerBody.addView(eyebrow);
        TextView headline=text("Musik, radio, dan perjalananmu.",width<800?15:18,ui);headline.setPadding(0,0,0,0);headline.setMaxLines(1);headline.setEllipsize(TextUtils.TruncateAt.END);bannerBody.addView(headline,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bannerButtons=row();addWeight(bannerButtons,button("Musik USB",()->actions.shortcut("music")),1,false);addWeight(bannerButtons,button("Navigasi",()->actions.shortcut("maps")),1,true);addWeight(bannerButtons,button("Android Auto",()->actions.shortcut("auto")),1,true);bannerBody.addView(bannerButtons,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout dock=row();LinearLayout.LayoutParams dockParams=new LinearLayout.LayoutParams(-1,0,.95f);dockParams.topMargin=dp(12);content.addView(dock,dockParams);
        for(int i=0;i<4;i++){final int slot=i;LinearLayout tile=card();tile.setPadding(dp(12),dp(8),dp(12),dp(8));Tile item=i<tiles.size()?tiles.get(i):null;
            LinearLayout tileTop=row();ImageView icon=new ImageView(a);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);if(item!=null)icon.setImageDrawable(item.icon);tileTop.addView(icon,new LinearLayout.LayoutParams(dp(26),dp(26)));TextView index=text(String.format(Locale.US,"%02d",i+1),14,dot);index.setGravity(Gravity.RIGHT);tileTop.addView(index,new LinearLayout.LayoutParams(0,-2,1));tile.addView(tileTop);
            TextView name=text(item==null?"Pilih favorit":item.label,width<800?12:15,ui);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);tile.addView(name,new LinearLayout.LayoutParams(-1,0,1));tile.setOnClickListener(v->actions.favorite(slot));tile.setOnLongClickListener(v->{actions.chooseFavorite(slot);return true;});tile.setFocusable(true);tile.setContentDescription((item==null?"Pilih favorit":item.label)+", tahan untuk mengganti");addWeight(dock,tile,1,i>0);
        }
        LinearLayout tray=card();tray.setGravity(Gravity.CENTER);tray.addView(new GridGlyph(a),new LinearLayout.LayoutParams(dp(30),dp(30)));TextView all=text("SEMUA APLIKASI",width<800?9:11,mono);all.setGravity(Gravity.CENTER);tray.addView(all);tray.setOnClickListener(v->actions.drawer());tray.setFocusable(true);tray.setContentDescription("Semua aplikasi, "+appCount+" aplikasi aktif");addWeight(dock,tray,1,true);
        refreshClock();
    }
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int fill,int border,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(1),border);return d;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(activity);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LinearLayout card(){LinearLayout r=new LinearLayout(activity);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(dp(width<800?10:14),dp(10),dp(width<800?10:14),dp(10));r.setBackground(shape(surface,line,16));return r;}
    private void addWeight(LinearLayout parent,View v,float weight,boolean gap){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,weight);if(gap)p.leftMargin=dp(10);parent.addView(v,p);}
    private TextView text(String s,int size,Typeface face){TextView t=new TextView(activity);t.setText(s);t.setTextSize(size);t.setTextColor(face==mono?muted:ink);t.setTypeface(face);t.setPadding(0,dp(3),0,dp(3));return t;}
    private TextView button(String label,Runnable click){TextView t=text(label,width<800?12:14,ui);t.setGravity(Gravity.CENTER);t.setPadding(dp(8),0,dp(8),0);t.setBackground(shape(light?0xffffffff:0xff28282c,line,24));t.setFocusable(true);t.setClickable(true);t.setOnClickListener(v->click.run());t.setOnFocusChangeListener((v,focused)->{t.setBackground(shape(focused?ink:light?0xffffffff:0xff28282c,line,24));t.setTextColor(focused?base:ink);});return t;}
    private void divider(LinearLayout c){View lineView=new View(activity);lineView.setBackgroundColor(line);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.topMargin=dp(6);p.bottomMargin=dp(4);c.addView(lineView,p);}
    void refreshClock(){Date now=new Date();String time=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now);SpannableString s=new SpannableString(time);if(time.length()>2)s.setSpan(new ForegroundColorSpan(light?0xffbd1724:0xffff4438),2,3,0);clock.setText(s);date.setText(new SimpleDateFormat("EEEE\ndd MMMM yyyy",new Locale("id","ID")).format(now).toUpperCase(new Locale("id","ID")));seconds.setText("DETIK "+new SimpleDateFormat("ss",Locale.US).format(now));
        if(System.currentTimeMillis()-lastNetwork>60000){lastNetwork=System.currentTimeMillis();try{ConnectivityManager cm=(ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);NetworkInfo info=cm.getActiveNetworkInfo();connection.setText(info!=null&&info.isConnected()?(info.getType()==ConnectivityManager.TYPE_WIFI?"● Wi-Fi tersambung":"● Jaringan tersambung"):"○ Offline · fitur lokal siap");}catch(Exception e){connection.setText("Fitur lokal siap");}}
    }
    void updateWeather(WeatherStore store,String error){weatherCity.setText(store.configured()?store.city().toUpperCase(Locale.ROOT):"CUACA LOKAL");WeatherReading w=store.cached();if(w==null){temperature.setText("—°");condition.setText(store.configured()?"Cuaca belum tersedia":"Atur kota cuaca");weatherDetail.setText(store.configured()?"Ketuk untuk memperbarui":"Tanpa akses GPS otomatis");forecast.setText(error==null?"Ketuk kartu untuk memilih kota":error);weatherAge.setText("Open-Meteo");return;}
        temperature.setText(Math.round(w.temperature)+"°C");weatherGlyph.code=w.code;weatherGlyph.invalidate();condition.setText(WeatherReading.label(w.code));weatherDetail.setText("Lembap "+Math.round(w.humidity)+"% · Angin "+Math.round(w.wind)+" km/j");forecast.setText(w.forecast.isEmpty()?"Prakiraan belum tersedia":w.forecast);String t=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(store.updated()));weatherAge.setText((error!=null||System.currentTimeMillis()-store.updated()>45*60000?"Cache · ":"Diperbarui · ")+t+" · Open-Meteo");}
    void updateMedia(MediaBridge bridge){MediaController c=bridge.current();MediaMetadata metadata=bridge.metadata();PlaybackState state=bridge.state();boolean playing=state!=null&&state.getState()==PlaybackState.STATE_PLAYING;
        String pkg=c==null?"":c.getPackageName();if(!lastPackage.equals(pkg)){lastPackage=pkg;lastSource="PEMUTAR MUSIK";if(c!=null)try{lastSource=activity.getPackageManager().getApplicationLabel(activity.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception ignored){}}
        assign(source,lastSource.toUpperCase(Locale.ROOT));String title=metadata==null?null:metadata.getString(MediaMetadata.METADATA_KEY_TITLE);String by=metadata==null?null:metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        assign(track,title==null||title.isEmpty()?(c==null?"Musik belum diputar":"Pemutar tersedia"):title);assign(artist,by==null||by.isEmpty()?"Ketuk untuk membuka pemutar":by);
        assign(badge,bridge.allowed()?(playing?"● MUSIK DIPUTAR":"KONTROL MUSIK SIAP"):"HUBUNGKAN KONTROL MUSIK");
        long duration=metadata==null?0:metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),position=state==null?0:Math.max(0,state.getPosition());if(playing&&state.getLastPositionUpdateTime()>0)position+=Math.round((SystemClock.elapsedRealtime()-state.getLastPositionUpdateTime())*state.getPlaybackSpeed());if(duration>0)position=Math.min(position,duration);
        boolean known=state!=null&&state.getPosition()>=0;
        progress.setProgress(duration<=0||!known?0:(int)Math.min(1000,position*1000/duration));mediaTime.setText(duration>0?(known?duration(position):"—:—")+" / "+duration(duration):playing?"Durasi belum tersedia":"—:— / —:—");
        if(wasPlaying==null||wasPlaying!=playing){wasPlaying=playing;play.setText(playing?"Ⅱ":"▶");play.setBackground(shape(playing?accent:light?0xffffffff:0xff28282c,line,24));play.setTextColor(playing?Color.WHITE:ink);}
        long actions=state==null?0:state.getActions();enable(previous,(actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0);enable(next,(actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0);enable(play,(actions&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))!=0);
        Bitmap art=metadata==null?null:metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(art==null&&metadata!=null)art=metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);if(art!=lastCover){lastCover=art;cover.setImageBitmap(art);}
    }
    private void enable(TextView v,boolean yes){v.setEnabled(yes);v.setAlpha(yes?1f:.35f);}
    private void assign(TextView view,String value){if(!TextUtils.equals(view.getText(),value))view.setText(value);}
    private final class GridGlyph extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        GridGlyph(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){p.setColor(ink);float cell=getWidth()/3f;for(int y=0;y<3;y++)for(int x=0;x<3;x++)canvas.drawCircle((x+.5f)*cell,(y+.5f)*cell,cell*.18f,p);}
    }
    private static String duration(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.US,"%d:%02d",s/60,s%60);}
    private final class Glyph extends View {int code=-1;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Glyph(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(ink);float w=getWidth(),h=getHeight(),y=h*.55f;
            if(code==0){float x=w*.5f,r=w*.18f;c.drawCircle(x,h*.5f,r,p);for(int i=0;i<8;i++){double angle=i*Math.PI/4;c.drawLine(x+(float)Math.cos(angle)*r*1.4f,h*.5f+(float)Math.sin(angle)*r*1.4f,x+(float)Math.cos(angle)*r*1.9f,h*.5f+(float)Math.sin(angle)*r*1.9f,p);}return;}
            Path path=new Path();path.moveTo(w*.2f,y+h*.1f);path.cubicTo(0,y+h*.02f,w*.05f,y-h*.2f,w*.28f,y-h*.17f);path.cubicTo(w*.36f,y-h*.43f,w*.72f,y-h*.4f,w*.77f,y-h*.13f);path.cubicTo(w,y-h*.15f,w,y+h*.15f,w*.77f,y+h*.15f);path.lineTo(w*.2f,y+h*.15f);c.drawPath(path,p);
            if(code>=51&&code<=99)for(int i=0;i<3;i++){float x=w*(.3f+i*.2f);c.drawLine(x,y+h*.25f,x-w*.05f,y+h*.34f,p);}
        }}
}
