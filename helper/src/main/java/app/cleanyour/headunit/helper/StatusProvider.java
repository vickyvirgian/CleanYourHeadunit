package app.cleanyour.headunit.helper;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import rikka.shizuku.Shizuku;

/** Read-only health probe available only to an authorized ADB shell or this app. */
public class StatusProvider extends ContentProvider {
    public static Bundle status(Context context) {
        Bundle b = new Bundle();
        boolean installed = false, running = false, granted = false;
        try { context.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0); installed = true; } catch (Exception ignored) {}
        try {
            running = Shizuku.pingBinder();
            granted = running && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {}
        b.putInt("protocol", 1);
        b.putBoolean("installed", installed);
        b.putBoolean("running", running);
        b.putBoolean("granted", granted);
        b.putBoolean("ready", installed && running && granted);
        return b;
    }
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (uid != 0 && uid != 2000 && uid != Process.myUid()) throw new SecurityException("ADB shell required");
        if (!"status".equals(method)) throw new IllegalArgumentException("Unknown method");
        long token = Binder.clearCallingIdentity();
        try { return status(getContext()); }
        finally { Binder.restoreCallingIdentity(token); }
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
