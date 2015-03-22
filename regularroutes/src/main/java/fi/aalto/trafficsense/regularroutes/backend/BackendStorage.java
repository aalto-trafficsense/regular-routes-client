package fi.aalto.trafficsense.regularroutes.backend;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;

import com.google.common.base.Optional;

import java.util.UUID;

import timber.log.Timber;

public class BackendStorage {
    /**
     * Backend Storage values:
     *
     * Installation id: uuid value that is generated when not exist (basically unique per app installation)
     * Session token: token that server provides for authenticated user
     * User id: hash value that both client+server generates using SHA256 from user's Google ID value
     * Onetime token: value that Google oauth server provides for client to send it to server for authentication
     *
     ***/

    private static final String FILE_NAME = "regularroutes";
    private static final String KEY_INSTALLATION_ID = "installation-id";
    private static final String KEY_SESSION_TOKEN = "device-token";
    private static final String KEY_USER_ID = "device-auth-id";
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


    /**
     * Get Installation id (ID that is unique per application installation)
     * Remarks: Installation ID is generated when it does not exist (on fresh installation case)
     **/
    public synchronized Optional<String> readInstallationId() {
        if (!mPreferences.contains(KEY_INSTALLATION_ID)) {
            final String newInstallationId = UUID.randomUUID().toString();
            mPreferences.edit().putString(KEY_INSTALLATION_ID, newInstallationId).commit();
        }

        return Optional.fromNullable(mPreferences.getString(KEY_INSTALLATION_ID, null));
    }

    public Optional<String> readSessionToken() {
        return Optional.fromNullable(mPreferences.getString(KEY_SESSION_TOKEN, null));
    }

    public void writeSessionToken(String sessionToken) {
        mPreferences.edit().putString(KEY_SESSION_TOKEN, sessionToken).commit();
        Timber.i("Session token saved: " + sessionToken);
    }

    public synchronized void clearSessionToken() {
        if (mPreferences.contains(KEY_SESSION_TOKEN)) {
            mPreferences.edit().remove(KEY_SESSION_TOKEN).commit();
            notifyPropertyChange(InternalBroadcasts.KEY_SESSION_TOKEN_CLEARED);
            Timber.i("Session token cleared");
        }
    }

    public synchronized boolean isUserIdAvailable() {
        return mPreferences.contains(KEY_USER_ID);
    }

    public synchronized Optional<String> readUserId() {
        return  Optional.fromNullable(mPreferences.getString(KEY_USER_ID, null));
    }

    public synchronized void writeUserId(String userId) {
        mPreferences.edit().putString(KEY_USER_ID, userId).commit();
        if (userId != null) {
            notifyPropertyChange(InternalBroadcasts.KEY_USER_ID_SET);
        }
        Timber.i("User id saved");
    }

    public synchronized void clearUserId() {
        if (mPreferences.contains(KEY_USER_ID)) {
            mPreferences.edit().remove(KEY_USER_ID).commit();
            notifyPropertyChange(InternalBroadcasts.KEY_USER_ID_CLEARED);
            Timber.i("User id cleared");
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

    public synchronized void writeOneTimeTokenAndUserId(String oneTimeToken, String userId) {
        writeOneTimeToken(oneTimeToken);
        writeUserId(userId);
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
