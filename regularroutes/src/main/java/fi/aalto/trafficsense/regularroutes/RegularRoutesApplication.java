package fi.aalto.trafficsense.regularroutes;

import android.app.Application;
import android.util.Log;
import timber.log.Timber;

public class RegularRoutesApplication extends Application {
    @Override
    public void onCreate() {
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        } else {
            Timber.plant(new ReleaseTree());
        }
        super.onCreate();
        Timber.i("Application started");
    }

    private static class ReleaseTree extends Timber.HollowTree {
        private static final String TAG = "RegularRoutes";

        @Override
        public void e(String message, Object... args) {
            Log.e(TAG, String.format(message, args));
        }

        @Override
        public void e(Throwable t, String message, Object... args) {
            Log.e(TAG, String.format(message, args), t);
        }
    }
}
