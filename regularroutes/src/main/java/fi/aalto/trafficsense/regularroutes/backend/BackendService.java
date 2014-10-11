package fi.aalto.trafficsense.regularroutes.backend;

import android.app.Service;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import edu.mit.media.funf.FunfManager;
import fi.aalto.trafficsense.regularroutes.util.LocalBinder;
import fi.aalto.trafficsense.regularroutes.util.LocalServiceConnection;
import timber.log.Timber;

public class BackendService extends Service {
    private final IBinder mBinder = new LocalBinder<BackendService>(this);

    private final ServiceConnection mFunfServiceConnection = new LocalServiceConnection<FunfManager>() {

        @Override
        protected FunfManager getService(IBinder binder) {
            return ((FunfManager.LocalBinder) binder).getManager();
        }

        @Override
        protected void onService(FunfManager service) {
            mFunfManager = service;
            onFunfReady();
        }
    };

    private FunfManager mFunfManager;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        bindService(new Intent(this, FunfManager.class), mFunfServiceConnection, BIND_AUTO_CREATE);

        Timber.d("Service started");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unbindService(mFunfServiceConnection);

        Timber.d("Service stopped");
    }

    private void onFunfReady() {
        mFunfManager.enablePipeline("default");
    }
}
