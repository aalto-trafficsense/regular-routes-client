package fi.aalto.trafficsense.regularroutes.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Pair;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.plus.Plus;

import java.io.IOException;

import fi.aalto.trafficsense.regularroutes.RegularRoutesApplication;
import fi.aalto.trafficsense.regularroutes.RegularRoutesConfig;
import timber.log.Timber;

/**
 * Created by Suomalainen on 1.3.2015.
 */
public class PlayServiceHelper {
    /* Static Members */
    public static final int RC_RECOVER_PLAY_SERVICES = 1001;
    public static final int RC_ID_QUERY = 1002;

    /* Private Members */
    Activity mActivity;

    /* Constructor(s) */
    public PlayServiceHelper(Activity context) {
        mActivity = context;
    }

    /* Public Methods */
    /**
     * Checks that play services is installed / up-to-date and prompts user to install it if not
     * or shows error message is it is not possible to recover
     * @return true, if play services is OK; false otherwise
     **/
    public boolean checkPlayServiceAvailability() {
        final Activity activity = mActivity;
        final int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);
        if (status != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(status)) {
                //show dialog to provide instructions to handle the problem //

                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        final Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, activity,
                                RC_RECOVER_PLAY_SERVICES);

                        if (dialog != null)
                            dialog.show();
                        else
                            showToast("Install Google Play services manually (automatic dialog show failed)");
                    }
                });

            } else {
                final String err = GooglePlayServicesUtil.getErrorString(status);
                showToast("Google Play Serivces check error: " + err);
            }
            return false;
        }
        return true;
    }

    /**
     * Show Google Play Services related resolution dialog if available. OnCancel is called
     * in case user cancels resolution or there is no resolution available.
     * Error string is then returned on callback.
     **/
    public void showError(final int errorCode, final Callback<String> onCancel) {
        if (GooglePlayServicesUtil.isUserRecoverableError(errorCode)) {
            final Dialog dlg = GooglePlayServicesUtil.getErrorDialog(
                    errorCode,
                    mActivity,
                    RC_RECOVER_PLAY_SERVICES,
                    new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            final String errMsg = GooglePlayServicesUtil.getErrorString(errorCode);
                            onCancel.run(errMsg, new RuntimeException(errMsg));
                        }

                    });
            if (dlg != null)
                dlg.show();
            else {
                Timber.w("Google Play service error dialog creation failed. Error code=" + errorCode);
            }
        } else {
            final String errMsg = GooglePlayServicesUtil.getErrorString(errorCode);
            onCancel.run(errMsg, new RuntimeException(errMsg));
        }
    }

    public String fetchAccountName(GoogleApiClient googleApiClient) {
        if (!googleApiClient.isConnected())
            return null;
        return Plus.AccountApi.getAccountName(googleApiClient);
    }

    /**
     * Fetch info object to be used with semi-anonymous client-server authentication
     **/
    public void fetchAuthenticationInfo(GoogleApiClient apiClient, Callback<AuthenticationInfo> callback) {
        new RetrieveAuthenticationInfoTask(callback, apiClient).execute();
    }


    /* Private Helpers */
    private void showToast(String msg) {
        if (msg == null)
            return;


        final String messageText = msg;
        final Activity activity = mActivity;
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, messageText, Toast.LENGTH_SHORT).show();
            }
        });

    }

    public class AuthenticationInfo {
        public final String hashValue;
        public final String oneTimeToken;

        public AuthenticationInfo(String id, String token) {
            oneTimeToken = token;
            hashValue = HashUtil.getHashStringFromId(id);
        }

    }

    private class RetrieveAuthenticationInfoTask extends AsyncTask<Void, Void, Pair<String, String>> {

        private final Callback<AuthenticationInfo> mCallback;
        private boolean mIdFetchFailed = false;
        private boolean mTokenFetchFailed = false;
        private final GoogleApiClient mApiClient;

        private RetrieveAuthenticationInfoTask(Callback<AuthenticationInfo> callback, GoogleApiClient apiClient) {
            mApiClient = apiClient;
            mCallback = callback;
        }

        @Override
        protected Pair<String, String> doInBackground(Void... params) {

            String idData = null;
            String tokenData = null;
            Context context = mActivity.getApplicationContext();
            final String accountName = Plus.AccountApi.getAccountName(mApiClient);
            // 1. Get account id
            try {
                idData = GoogleAuthUtil.getAccountId(context, accountName);
                Timber.i("Google id: " + idData);

            } catch (UserRecoverableAuthException e) {
                Timber.w("Retrieving id user recoverable auth ex: " + e.getMessage());
                mActivity.startActivityForResult(e.getIntent(), RC_ID_QUERY);
                mIdFetchFailed = true;
            } catch (GoogleAuthException e) {
                Timber.e("Retrieving id error: " + e.getMessage());
                mIdFetchFailed = true;
                idData = e.getMessage();
            } catch (IOException e) {
                Timber.e("Retrieving id error: " + e.getMessage());
                idData = e.getMessage();
                mIdFetchFailed = true;
            }

            final RegularRoutesConfig config = ((RegularRoutesApplication) mActivity.getApplication()).getConfig();
            final String web_cl_id = config.serverClientId;
            final String scopes = "oauth2:server:client_id:" + web_cl_id + ":api_scope:profile";
            // 2. Get one time token
            if (!mIdFetchFailed) {
                try {
                    tokenData = GoogleAuthUtil.getToken(context, accountName, scopes);
                    GoogleAuthUtil.invalidateToken(context, tokenData);

                    Timber.i("Token fetched from google: " + tokenData);
                } catch (UserRecoverableAuthException e) {
                    Timber.w("Retrieving token recoverable auth ex: " + e.getMessage());

                    mActivity.startActivityForResult(e.getIntent(), RC_ID_QUERY);
                } catch (IOException e) {
                    Timber.e("Retrieving token error: " + e.getMessage());
                    mTokenFetchFailed = true;
                    tokenData = e.getMessage();
                } catch (GoogleAuthException e) {
                    Timber.e("Retrieving token error: " + e.getMessage());
                    mTokenFetchFailed = true;
                    tokenData = e.getMessage();
                }
            }

            return new Pair<>(idData, tokenData);
        }

        @Override
        protected void onPostExecute(Pair<String, String> result) {
            super.onPostExecute(result);

            if (mCallback != null) {

                if (!mIdFetchFailed && !mTokenFetchFailed)
                    mCallback.run(new AuthenticationInfo(result.first, result.second), null);
                else if (mIdFetchFailed) // result has error message instead of id
                    mCallback.run(null, new RuntimeException(result.first));
                else
                    mCallback.run(null, new RuntimeException(result.second));
            }

        }
    }
}
