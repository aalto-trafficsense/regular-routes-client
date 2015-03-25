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

    private static final String FUNF_PIPELINE_NAME = "default";

    /* Private Members */
    private boolean isRunning = false;

    private final IBinder mBinder = new LocalBinder<BackendService>(this);
    private FunfManager mFunfManager;
    private final ServiceConnection mFunfServiceConnection = new LocalServiceConnection<FunfManager>() {

        @Override
        protected FunfManager getService(IBinder binder) {
            return ((FunfManager.LocalBinder) binder).getManager();
        }

        @Override
        protected void onService(FunfManager service) {
            mFunfManager = service;
            onFunfReady();

            Timber.d("funf ready");
        }
    };

    /* Overridden Methods */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.isRunning = true;
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startAndBindFunfService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        setRunning(false);
    }

    /* Getters and Setters */
    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean isRunning) {
        if (this.isRunning != isRunning) {
            if (isRunning) {
                startAndBindFunfService();
                startService(new Intent(BackendService.this, BackendService.class));
                Timber.d("BackgroundService: Started self");
            } else {
                stopAndUnbindFunfService();
                stopSelf();
                Timber.d("BackgroundService: Stopped self");
            }
            this.isRunning = isRunning;
        }
    }


    /* Private Methods */
    private void startAndBindFunfService() {
        Intent intent = new Intent(this, FunfManager.class);
        startService(intent);
        bindService(intent, mFunfServiceConnection, 0);
    }

    private void stopAndUnbindFunfService() {
        unbindService(mFunfServiceConnection);
        stopService(new Intent(this, FunfManager.class));
    }

    private void onFunfReady() {
        mFunfManager.enablePipeline(FUNF_PIPELINE_NAME);
    }

}
