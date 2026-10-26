package com.dotdrive.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.*;
import android.graphics.*;
import java.io.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w1024dp-h600dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DashboardTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    @Before public void open(){
        String[][] fixtures={{"com.hcn.autoradio","Radio"},{"com.hcn.AutoMediaPlayer","Music"},{"com.mediatek.filemanager","File Manager"},{"com.android.settings","Car Settings"}};
        Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for(String[] fixture:fixtures){
            PackageInfo p=new PackageInfo();p.packageName=fixture[0];p.applicationInfo=new ApplicationInfo();p.applicationInfo.packageName=p.packageName;p.applicationInfo.enabled=true;p.applicationInfo.nonLocalizedLabel=fixture[1];
            ActivityInfo a=new ActivityInfo();a.packageName=p.packageName;a.name=p.packageName+".MainActivity";a.applicationInfo=p.applicationInfo;a.enabled=true;a.exported=true;a.nonLocalizedLabel=fixture[1];p.activities=new ActivityInfo[]{a};
            shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).installPackage(p);
            ResolveInfo r=new ResolveInfo();r.activityInfo=a;r.nonLocalizedLabel=fixture[1];shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).addResolveInfoForIntent(launcher,r);
        }
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();layout();
    }
    @After public void close(){controller.pause().stop().destroy();}
    private View root(){return activity.findViewById(android.R.id.content);}
    private void layout(){View v=root();v.measure(View.MeasureSpec.makeMeasureSpec(1024,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(535,View.MeasureSpec.EXACTLY));v.layout(0,0,1024,535);}
    private TextView find(View v,String s){if(v instanceof TextView && s.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView t=find(g.getChildAt(i),s);if(t!=null)return t;}}return null;}
    @Test public void dailyDashboardWorksWithoutAndroidAutoOrWeatherPermission(){
        assertNotNull(find(root(),"RADIO"));assertNotNull(find(root(),"BLUETOOTH"));assertNotNull(find(root(),"BERKAS"));
        assertNotNull(find(root(),"Atur kota cuaca"));assertNotNull(find(root(),"Musik belum diputar"));
        TextView button=find(root(),"Musik USB");assertNotNull(button);assertTrue(button.getHeight()>=44);
        TextView drawer=find(root(),"SEMUA APLIKASI");assertNotNull(drawer);assertTrue(drawer.getHeight()>0);
    }
    @Test public void lightThemeAndDrawerRemainUsable(){
        find(root(),"TERANG").performClick();layout();assertNotNull(find(root(),"Musik USB"));
        View tray=(View)find(root(),"SEMUA APLIKASI").getParent();tray.performClick();layout();assertNotNull(find(root(),"SEMUA APLIKASI"));
    }
    @Test public void wallpaperNeedsUserSelectedImage(){
        find(root(),"TRANSPARAN").performClick();Intent intent=shadowOf(activity).getNextStartedActivity();
        assertNotNull(intent);assertEquals(Intent.ACTION_OPEN_DOCUMENT,intent.getAction());assertEquals("image/*",intent.getType());
    }
    @Test public void renderDashboardThemesAtHeadunitSize() throws Exception {
        save("dark");find(root(),"TERANG").performClick();layout();save("light");
    }
    private void save(String mode) throws Exception {
        Bitmap image=Bitmap.createBitmap(1024,535,Bitmap.Config.ARGB_8888);root().draw(new Canvas(image));
        File dir=new File("build/dashboard-preview");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(OutputStream out=new FileOutputStream(new File(dir,"dashboard-"+mode+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
        assertNotEquals(0,image.getPixel(0,0));
    }
}
