package fi.aalto.trafficsense.regularroutes.backend;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.common.base.Optional;
import timber.log.Timber;

public class BackendStorage {
    private static final String FILE_NAME = "regularroutes";
    private static final String KEY_DEVICE_TOKEN = "device-token";

    private final SharedPreferences mPreferences;

    public BackendStorage(SharedPreferences mPreferences) {
        this.mPreferences = mPreferences;
    }

    public static BackendStorage create(Context context) {
        return new BackendStorage(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE));
    }

    public Optional<String> readDeviceToken() {
        return Optional.fromNullable(mPreferences.getString(KEY_DEVICE_TOKEN, null));
    }

    public void writeDeviceToken(String deviceToken) {
        mPreferences.edit().putString(KEY_DEVICE_TOKEN, deviceToken).commit();
        Timber.i("Device token saved");
    }

    public void clearDeviceToken() {
        mPreferences.edit().remove(KEY_DEVICE_TOKEN).commit();
        Timber.i("Device token cleared");
    }
}
