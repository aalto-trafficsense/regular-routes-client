package fi.aalto.trafficsense.regularroutes;

import android.app.Application;
import android.os.Environment;
import android.util.Log;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Closeables;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import timber.log.Timber;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class RegularRoutesApplication extends Application {
    private RegularRoutesConfig config;

    @Override
    public void onCreate() {
        Config rawConfig = parseConfigFromAssets();
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
            rawConfig =
                    parseConfigFromExternalStorage()
                    .withFallback(parseConfigFromFilesDir())
                    .withFallback(rawConfig);
        } else {
            Timber.plant(new ReleaseTree());
        }
        config = RegularRoutesConfig.create(rawConfig);
        Timber.i("Using configuration %s", config);
        super.onCreate();
        Timber.i("Application started");
    }

    private Config parseConfigFromAssets() {
        InputStream stream = null;
        try {
            stream = getAssets().open("regularroutes.conf");
            return ConfigFactory.parseReader(new InputStreamReader(stream, Charsets.UTF_8));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            Closeables.closeQuietly(stream);
        }
    }

    private Config parseConfigFromFilesDir() {
        File file = new File(getFilesDir(), "regularroutes.conf");
        if (!file.exists())
            return ConfigFactory.empty();

        Config config = ConfigFactory.parseFile(file);
        return fallbackOnDefaultConfigsIfRequired(config);
    }

    private Config parseConfigFromExternalStorage() {
        File file = new File(Environment.getExternalStorageDirectory(), "regularroutes.conf");
        if (!file.exists())
            return ConfigFactory.empty();

        Config config = ConfigFactory.parseFile(file);
        return fallbackOnDefaultConfigsIfRequired(config);
    }

    public RegularRoutesConfig getConfig() {
        return config;
    }

    private Config fallbackOnDefaultConfigsIfRequired(final Config config) {
        /**
         * Quick fix for handling changed configurations:
         * Restore default, if new configurations are added or old configs removed
         **/

        Config defaultConf = parseConfigFromAssets();
        if (config.entrySet().size() != defaultConf.entrySet().size())
            return defaultConf;
        else
            return config;
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
