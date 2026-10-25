package live.pharmaradio.reponsevoice.autonome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;

public class BridgeProvider extends ContentProvider {
    static final String DESCRIPTOR = "live.pharmaradio.reponsevoice.autonome.CallAudio";
    static final String AUTHORITY = "live.pharmaradio.reponsevoice.autonome.bridge";
    private static volatile IBinder helper;

    static IBinder getHelper() {
        IBinder binder = helper;
        return binder != null && binder.isBinderAlive() ? binder : null;
    }

    @Override
    public boolean onCreate() { return true; }

    @Override
    public synchronized Bundle call(String method, String argument, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (!"publish".equals(method) || uid != 2000 || extras == null) throw new SecurityException("Publication shell uniquement");
        IBinder candidate = extras.getBinder("bridge");
        if (candidate == null || !candidate.isBinderAlive()) throw new IllegalArgumentException("Binder absent");
        if (helper != candidate) {
            try {
                candidate.linkToDeath(() -> {
                    if (helper == candidate) helper = null;
                }, 0);
            } catch (Exception error) { throw new IllegalStateException("Binder arrete"); }
            helper = candidate;
        }
        Bundle result = new Bundle();
        result.putInt("app_uid", Process.myUid());
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] arguments, String order) { throw new UnsupportedOperationException(); }
    @Override
    public String getType(Uri uri) { return null; }
    @Override
    public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override
    public int delete(Uri uri, String selection, String[] arguments) { throw new UnsupportedOperationException(); }
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] arguments) { throw new UnsupportedOperationException(); }
}
