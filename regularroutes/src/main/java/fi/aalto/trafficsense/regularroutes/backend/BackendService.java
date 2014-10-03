package fi.aalto.trafficsense.regularroutes.backend;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import fi.aalto.trafficsense.regularroutes.util.LocalBinder;
import timber.log.Timber;

public class BackendService extends Service {
    private final IBinder mBinder = new LocalBinder<BackendService>(this);

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // TODO: Start backend stuff

        Timber.d("Service started");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Timber.d("Service stopped");
    }
}
