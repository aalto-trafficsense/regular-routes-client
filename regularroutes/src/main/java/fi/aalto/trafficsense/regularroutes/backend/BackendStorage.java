package fi.aalto.trafficsense.regularroutes.backend;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;

import com.google.common.base.Optional;

import timber.log.Timber;

public class BackendStorage {
    private static final String FILE_NAME = "regularroutes";
    private static final String KEY_DEVICE_TOKEN = "device-token";
    private static final String KEY_DEVICE_AUTH_ID = "device-auth-id";
    private static final String KEY_ONE_TIME_TOKEN =  "one-time-token";

    private final SharedPreferences mPreferences;
    private final LocalBroadcastManager mLocalBroadcastManager;

    public BackendStorage(Context context) {
        mPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(context);
    }

    public static BackendStorage create(Context context) {
        return new BackendStorage(context);
    }

    public Optional<String> readSessionToken() {
        return Optional.fromNullable(mPreferences.getString(KEY_DEVICE_TOKEN, null));
    }

    public void writeSessionToken(String deviceToken) {
        mPreferences.edit().putString(KEY_DEVICE_TOKEN, deviceToken).commit();
        Timber.i("Session token saved: " + deviceToken);
    }

    public void clearSessionToken() {
        mPreferences.edit().remove(KEY_DEVICE_TOKEN).commit();
        Timber.i("Session token cleared");
    }

    public synchronized boolean isDeviceAuthIdAvailable() {
        return mPreferences.contains(KEY_DEVICE_AUTH_ID);
    }

    public synchronized Optional<String> readDeviceAuthId() {
        return  Optional.fromNullable(mPreferences.getString(KEY_DEVICE_AUTH_ID, null));
    }

    public synchronized void writeDeviceAuthId(String deviceAuthId) {
        mPreferences.edit().putString(KEY_DEVICE_AUTH_ID, deviceAuthId).commit();
        if (deviceAuthId != null) {
            notifyPropertyChange(InternalBroadcasts.KEY_AUTH_TOKEN_SET);
        }
        Timber.i("Device authentication id saved");
    }

    public synchronized void clearDeviceAuthId() {
        if (mPreferences.contains(KEY_DEVICE_AUTH_ID)) {
            mPreferences.edit().remove(KEY_DEVICE_AUTH_ID).commit();
            notifyPropertyChange(InternalBroadcasts.KEY_AUTH_TOKEN_CLEARED);
            Timber.i("Device authentication id cleared");
        }

    }

    public synchronized boolean isOneTimeTokenAvailable() {
        return mPreferences.contains(KEY_ONE_TIME_TOKEN);
    }

    public synchronized Optional<String> readAndClearOneTimeToken() {
        Optional<String> token = Optional.fromNullable(mPreferences.getString(KEY_ONE_TIME_TOKEN, null));
        clearOneTimeToken();
        return token;
    }

    public synchronized void writeOneTimeToken(String oneTimeToken) {
        mPreferences.edit().putString(KEY_ONE_TIME_TOKEN, oneTimeToken).commit();
        if (oneTimeToken != null) {
            notifyPropertyChange(InternalBroadcasts.KEY_ONE_TIME_TOKEN_SET);
        }
        Timber.i("One-time token saved");
    }

    public synchronized void writeOneTimeTokenAndDeviceAuthId(String oneTimeToken, String deviceAuthId) {
        writeOneTimeToken(oneTimeToken);
        writeDeviceAuthId(deviceAuthId);
    }

    public synchronized void clearOneTimeToken() {
        if (mPreferences.contains(KEY_ONE_TIME_TOKEN)) {
            mPreferences.edit().remove(KEY_ONE_TIME_TOKEN).commit();
            notifyPropertyChange(InternalBroadcasts.KEY_ONE_TIME_TOKEN_CLEARED);
            Timber.i("One-time token cleared");
        }
    }

    private void notifyPropertyChange(String changeType) {
        if (mLocalBroadcastManager != null)
        {
            Intent intent = new Intent(changeType);
            mLocalBroadcastManager.sendBroadcast(intent);
        }
    }
}
