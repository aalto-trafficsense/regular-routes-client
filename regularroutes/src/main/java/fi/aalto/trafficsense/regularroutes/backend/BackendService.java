package fi.aalto.trafficsense.regularroutes.backend;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import fi.aalto.trafficsense.regularroutes.util.LocalBinder;

public class BackendService extends Service {
    private static final String TAG = BackendService.class.getSimpleName();

    private final IBinder mBinder = new LocalBinder<BackendService>(this);

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // TODO: Start backend stuff

        Log.d(TAG, "Service started");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "Service stopped");
    }
}
