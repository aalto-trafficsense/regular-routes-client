package fi.aalto.trafficsense.regularroutes.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.plus.Plus;

import java.util.concurrent.atomic.AtomicReference;

import fi.aalto.trafficsense.regularroutes.R;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.backend.InternalBroadcasts;
import fi.aalto.trafficsense.regularroutes.backend.RegularRoutesPipeline;
import fi.aalto.trafficsense.regularroutes.util.Callback;
import fi.aalto.trafficsense.regularroutes.util.PlayServiceHelper;
import timber.log.Timber;

/**
 * Google+ authentication
 * This activity follows the logic defined on:
 * - https://developers.google.com/+/mobile/android/getting-started
 * - https://developers.google.com/+/mobile/android/sign-in
 *
 **/

public class LoginActivity extends Activity
        implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    /* Request code used to invoke sign in user interactions. */
    private static final int RC_SIGN_IN = 0;
    private static final int STATE_DEFAULT = 0;
    private static final int STATE_SIGN_IN = 1;
    private static final int STATE_IN_PROGRESS = 2;
    private static final String KEY_SAVED_PROGRESS = "sign_in_progress";
    private static final String KEY_UPLOAD_ENABLED_STATE = "upload_enabled_state";
    private static final String TAG_ERR_DIALOG = "error_dialog";

    // UI references.
    private View mProgressView;
    private SignInButton mSignInButton;
    private Button mSignOutButton;
    private Button mRevokeButton;
    private Button mCancelButton;
    private Button mContinueButton;
    private TextView mStatus;
    private TextView mConnectionStatus;

    // Other members
    private final AtomicReference<Boolean> mAuthenticationOngoing = new AtomicReference<>(false);


    /* Store the connection result from onConnectionFailed callbacks so that we can
     * resolve them when the user clicks sign-in. */
    private ConnectionResult mConnectionResult;

    // We use mSignInProgress to track whether user has clicked sign in.
    // mSignInProgress can be one of three values:
    //
    //       STATE_DEFAULT: The default state of the application before the user
    //                      has clicked 'sign in', or after they have clicked
    //                      'sign out'.  In this state we will not attempt to
    //                      resolve sign in errors and so will display our
    //                      Activity in a signed out state.
    //       STATE_SIGN_IN: This state indicates that the user has clicked 'sign
    //                      in', so resolve successive errors preventing sign in
    //                      until the user has successfully authorized an account
    //                      for our app.
    //   STATE_IN_PROGRESS: This state indicates that we have started an intent to
    //                      resolve an error, and so we should not start further
    //                      intents until the current intent completes.
    private int mSignInProgress;

    private AtomicReference<Boolean> mClearAccountTriggered = new AtomicReference<>(false);
    private GoogleApiClient mGoogleApiClient;
    private PlayServiceHelper mPlayServiceHelper;
    private BackendStorage mStorage;
    private boolean mUploadEnabledState;
    private Handler mHandler = new Handler();


    private BroadcastReceiver mMessageReceiver;

    private final Callback<Boolean> mFetchAndSetAuthenticationTokensCallback = new Callback<Boolean>() {
        @Override
        public void run(Boolean result, RuntimeException error) {
            if (result) {
                // succeeded
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStatus.setText(getResources().getString(R.string.status_authenticated));
                        setSignInProgress(STATE_DEFAULT);

                        // Return upload enabled state to previous state
                        RegularRoutesPipeline.setUploadEnabledState(mUploadEnabledState);
                    }

                });
            } else {
                // failed with error
                final String errText = error.getMessage();
                Timber.e("fetchAndSetAuthenticationTokens: " + errText);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setSignInProgress(STATE_DEFAULT);
                        final String statusText = getResources().getString(R.string.status_authentication_failed);
                        mStatus.setText(statusText);
                        showErrorInDialog(errText);
                    }

                });
            }
            mAuthenticationOngoing.set(false);

        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        initMembers(savedInstanceState);

        if (!isSignedIn())
            // Turn off uploading while authenticating
            RegularRoutesPipeline.setUploadEnabledState(false);

        initBroadcastReceiver();

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isSignedIn()) {
            mSignInButton.setEnabled(false);
            setStatusAsAuthenticated();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Connect, if not authenticated already
        mGoogleApiClient.connect();

    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SAVED_PROGRESS, mSignInProgress);
        outState.putBoolean(KEY_UPLOAD_ENABLED_STATE, mUploadEnabledState);
        if (mUploadEnabledState)
            mSignInButton.setEnabled(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int responseCode, Intent intent) {
        if (requestCode == RC_SIGN_IN
                || requestCode == PlayServiceHelper.RC_RECOVER_PLAY_SERVICES ) {
            if (responseCode == RESULT_OK) {
                // If the error resolution was successful we should continue
                // processing errors.
                setSignInProgress(STATE_SIGN_IN);
            }else {
                // If the error resolution was not successful or the user canceled,
                // we should stop processing errors.
                onGoogleSignInCompleted();
            }


            if (!mGoogleApiClient.isConnecting()) {
                // If Google Play services resolved the issue with a dialog then
                // onStart is not called so we need to re-attempt connection here.
                mGoogleApiClient.connect();
            }
            return;
        }
        if (requestCode == PlayServiceHelper.RC_ID_QUERY) {
            if (responseCode == RESULT_OK) {
                // Re-fetch authentication
                fetchAndSetAuthenticationTokens(mFetchAndSetAuthenticationTokensCallback);
            }
            else
                signOutUser();
        }
    }



    @Override
    public void onConnected(Bundle connectionHint) {
        if (mClearAccountTriggered.get()) {
            // Connection was established to sign out
            mClearAccountTriggered.set(false);
            clearAccountAndReconnect();
            return;
        }

        setUiControlStates(true);

        if (isSignedIn()) {
            // No need to get device authentication id
            doSignInActions();
            return;
        }

        if (mAuthenticationOngoing.get())
            return;

        mAuthenticationOngoing.set(true);
        // Indicate that the sign in process is complete.
        mStatus.setText(getResources().getString(R.string.status_fetch_auth_tokens));
        Timber.i("Login: onConnected");
        fetchAndSetAuthenticationTokens(mFetchAndSetAuthenticationTokensCallback);
    }



    @Override
    public void onConnectionSuspended(int i) {
        // reconnect
        mGoogleApiClient.connect();
    }

    @Override
        public void onConnectionFailed(ConnectionResult result) {
        try {
            final int errorCode = result.getErrorCode();
            if (errorCode == ConnectionResult.API_UNAVAILABLE) {
                // An API requested for GoogleApiClient is not available. The device's current
                // configuration might not be supported with the requested API or a required component
                // may not be installed, such as the Android Wear application. You may need to use a
                // second GoogleApiClient to manage the application's optional APIs.

                mPlayServiceHelper.showError(errorCode, new Callback<String>() {
                    @Override
                    public void run(String result, RuntimeException error) {
                        showErrorInDialog(result);
                    }
                });
                return;
            }

            if (mSignInProgress == STATE_IN_PROGRESS)
                return; // Ignore, when connection attempt is ongoing


            // We do not have an intent in progress so we should store the latest
            // connection result for use when the sign in button is clicked.
            mConnectionResult = result;



            if (mSignInProgress == STATE_SIGN_IN) {
                // STATE_SIGN_IN indicates the user already clicked the sign in button
                // so we should continue processing errors until the user is signed in
                // or they click cancel.
                resolveSignInError();
            }
        } finally {
            // user is signed out when it does not have working connection after connection attempt
            onSignedOut();
        }


    }


    @Override
    public void onClick(View v) {
        // login button click event handlers:
        if (!mGoogleApiClient.isConnecting()) {
            // We only process button clicks when GoogleApiClient is not transitioning
            // between connected and not connected.
            switch (v.getId()) {
                case R.id.plus_sign_in_button:
                    mStatus.setText(R.string.status_signing_in);

                    if (v.getId() == R.id.plus_sign_in_button
                            && !mGoogleApiClient.isConnecting()) {
                        resolveSignInError();
                    }
                    //resolveSignInError();
                    break;
                case R.id.plus_sign_out_button:
                    signOutUser();
                    break;
                case R.id.plus_disconnect_button:
                    revokeAccess();
                    break;
                case R.id.login_continue_button:
                case R.id.login_close_button:
                    close(true);
                    break;
            }
        }
    }

    /* Private Helper Methods */
    private void initMembers(Bundle savedInstanceState) {
        mStatus = (TextView)findViewById(R.id.sign_in_status);
        mConnectionStatus = (TextView)findViewById(R.id.connection_status);
        mSignInButton = (SignInButton) findViewById(R.id.plus_sign_in_button);
        mSignOutButton = (Button) findViewById(R.id.plus_sign_out_button);
        mRevokeButton = (Button) findViewById(R.id.plus_disconnect_button);
        mCancelButton = (Button) findViewById(R.id.login_close_button);
        mContinueButton = (Button) findViewById(R.id.login_continue_button);
        mProgressView = findViewById(R.id.login_progress);


        /* Button Handlers */
        mSignInButton.setOnClickListener(this);
        mSignOutButton.setOnClickListener(this);
        mRevokeButton.setOnClickListener(this);
        mCancelButton.setOnClickListener(this);
        mContinueButton.setOnClickListener(this);

        /* Restoration */
        mUploadEnabledState = RegularRoutesPipeline.isUploadEnabled();
        if (savedInstanceState != null) {
            setSignInProgress(savedInstanceState.getInt(KEY_SAVED_PROGRESS, STATE_DEFAULT));
            if (savedInstanceState.containsKey(KEY_UPLOAD_ENABLED_STATE))
                mUploadEnabledState = savedInstanceState.getBoolean(KEY_UPLOAD_ENABLED_STATE, true);
        }

        /* Other Members */
        mGoogleApiClient = buildGoogleApiClient();
        mPlayServiceHelper = new PlayServiceHelper(this);
        mStorage = BackendStorage.create(this);
    }

    private void initBroadcastReceiver() {
        mMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                switch (action) {
                    case InternalBroadcasts.KEY_USER_ID_CLEARED:
                        // Sign-out when authentication token is cleared (e.g. reg. failed)
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // The current state should be verified because this message may be delayed
                                if (!mStorage.isUserIdAvailable()) {
                                    Toast.makeText(getBaseContext(), "Signed out (or sign-in failed)", Toast.LENGTH_LONG).show();
                                    signOutUser();
                                }
                            }
                        });
                        break;
                    case InternalBroadcasts.KEY_USER_ID_SET:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // The current state should be verified because this message may be delayed
                                if (mStorage.isUserIdAvailable()) {
                                    doSignInActions();
                                }
                            }
                        });
                        break;
                    case InternalBroadcasts.KEY_ONE_TIME_TOKEN_SET:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // The current state should be verified because this message may be delayed
                                if (mStorage.isOneTimeTokenAvailable()) {
                                    final String statusText = getResources().getString(R.string.status_connecting_to_server);
                                    mStatus.setText(statusText);
                                }
                            }
                        });
                        break;
                    case InternalBroadcasts.KEY_SERVER_CONNECTION_FAILURE:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mConnectionStatus.setText(getResources().getString(R.string.connection_status_connection_failed));
                            }
                        });
                        break;
                    case InternalBroadcasts.KEY_SERVER_CONNECTION_SUCCEEDED:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                final String connStatusText = getResources().getString(R.string.connection_status_connected);
                                if (!mConnectionStatus.getText().equals(connStatusText))
                                    mConnectionStatus.setText(connStatusText);
                            }
                        });
                        break;
                    case InternalBroadcasts.KEY_REGISTRATION_SUCCEEDED:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setStatusAsAuthenticated();
                            }
                        });
                        break;
                    case InternalBroadcasts.KEY_AUTHENTICATION_SUCCEEDED:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setStatusAsAuthenticated();
                            }
                        });
                        break;
                    case InternalBroadcasts.KEY_AUTHENTICATION_FAILED:
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                final String statusText = getResources().getString(R.string.status_authentication_failed);
                                if (!mStatus.getText().equals(statusText))
                                    mStatus.setText(statusText);
                            }
                        });
                        break;
                }
            }
        };

        final IntentFilter filter = new IntentFilter();
        filter.addAction(InternalBroadcasts.KEY_USER_ID_CLEARED);
        filter.addAction(InternalBroadcasts.KEY_USER_ID_SET);
        filter.addAction(InternalBroadcasts.KEY_ONE_TIME_TOKEN_SET);
        filter.addAction(InternalBroadcasts.KEY_REGISTRATION_SUCCEEDED);
        filter.addAction(InternalBroadcasts.KEY_SERVER_CONNECTION_FAILURE);
        filter.addAction(InternalBroadcasts.KEY_SERVER_CONNECTION_SUCCEEDED);
        filter.addAction(InternalBroadcasts.KEY_AUTHENTICATION_SUCCEEDED);
        filter.addAction(InternalBroadcasts.KEY_AUTHENTICATION_FAILED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, filter);
    }

    private void setStatusAsAuthenticated() {
        final String statusText = getResources().getString(R.string.status_authenticated);
        if (!mStatus.getText().equals(statusText))
            mStatus.setText(statusText);
    }

    private void doSignInActions() {

        setSignInProgress(STATE_DEFAULT);
        mContinueButton.setVisibility(View.VISIBLE);
    }

    private void revokeAccess() {
        RegularRoutesPipeline.setUploadEnabledState(false);
        mStorage.clearSessionToken();
        mStorage.clearOneTimeToken();
        mStorage.clearUserId();
        // After we revoke permissions for the user with a GoogleApiClient
        // instance, we must discard it and create a new one.
        Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
        // Our sample has caches no user data from Google+, however we
        // would normally register a callback on revokeAccessAndDisconnect
        // to delete user data so that we comply with Google developer
        // policies.
        Plus.AccountApi.revokeAccessAndDisconnect(mGoogleApiClient);
        mGoogleApiClient = buildGoogleApiClient();
        mGoogleApiClient.connect();

    }



    private void signOutUser() {
        mContinueButton.setVisibility(View.INVISIBLE);
        RegularRoutesPipeline.setUploadEnabledState(false);
        mStorage.clearSessionToken();
        mStorage.clearOneTimeToken();
        mStorage.clearUserId();

        // We clear the default account on sign out so that Google Play
        // services will not return an onConnected callback without user
        // interaction.
        if (mGoogleApiClient.isConnected()) {
            clearAccountAndReconnect();
        }
        else {
            mClearAccountTriggered.set(true);
            mGoogleApiClient.connect();
        }

    }

    private void clearAccountAndReconnect() {
        Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
        mGoogleApiClient.disconnect();
        mGoogleApiClient.connect();
    }


    /**
     * Method that triggers fetching authentication
     **/
    private void fetchAndSetAuthenticationTokens(final Callback<Boolean> onCompleted) {
        final int retryInterval = 2000;

        final Runnable r = new Runnable() {
            @Override
            public void run() {
                if (!mGoogleApiClient.isConnected()) {
                    Timber.i("Reconnecting GoogleApiClient");
                    reconnectIfRequired();
                    mHandler.postDelayed(this, retryInterval);
                    return;
                }

                mPlayServiceHelper.fetchAuthenticationInfo(mGoogleApiClient,
                        new Callback<PlayServiceHelper.AuthenticationInfo>() {
                    @Override
                    public void run(PlayServiceHelper.AuthenticationInfo result, RuntimeException error) {
                        String resultErrorMessage = null;
                        boolean succeeded = error == null;
                        // Account info fetched
                        if (error != null) {
                            Timber.e("Error in authentication token fetch: " + error.getMessage());
                            resultErrorMessage = error.getMessage();
                        }
                        else {
                            if (result.oneTimeToken == null) {
                                // no token received
                                Timber.w("authentication token was null");
                                return;
                            }
                            // Set device authentication values that are used in RestAPI
                            Timber.i("Setting auth data: token=" + result.oneTimeToken + ", hash=" + result.hashValue);
                            mStorage.writeOneTimeTokenAndUserId(result.oneTimeToken, result.hashValue);
                        }
                        if (onCompleted != null) {
                            if (succeeded)
                                onCompleted.run(true, null);
                            else
                                onCompleted.run(false, new RuntimeException(resultErrorMessage));

                        }

                    }
                });
            }
        };

        if (mGoogleApiClient.isConnected()) {
            new Thread(r).start();
            return;
        }

        final Runnable delayed = new Runnable() {
            @Override
            public void run() {
                if (mGoogleApiClient.isConnected()) {
                    new Thread(r).start();
                } else {
                    reconnectIfRequired();
                    Timber.d("Posting account name fetch by" + retryInterval + " milliseconds");
                    mHandler.postDelayed(this, retryInterval);
                }


            }
        };
        mHandler.postDelayed(delayed, retryInterval);
    }

    private void reconnectIfRequired() {
        if (mSignInProgress == STATE_IN_PROGRESS)
            return;

        if (mGoogleApiClient.isConnected() ||mGoogleApiClient.isConnecting())
            return;

        mGoogleApiClient.connect();
    }

    /**
     * A helper method to resolve the current ConnectionResult error from Google+.
     */
    private void resolveSignInError() {
        if (mConnectionResult == null)
            return;

        if (mConnectionResult.hasResolution()) {
            try {
                setSignInProgress(STATE_IN_PROGRESS);
                startIntentSenderForResult(mConnectionResult.getResolution().getIntentSender(),
                        RC_SIGN_IN, null, 0, 0, 0);
            } catch (IntentSender.SendIntentException e) {
                // The intent was canceled before it was sent.  Return to the default
                // state and attempt to connect to get an updated ConnectionResult.
                setSignInProgress(STATE_SIGN_IN);
                mGoogleApiClient.connect();
            }
        }
        else {

            final int errorCode = mConnectionResult.getErrorCode();
            mPlayServiceHelper.showError(errorCode, new Callback<String>() {
                @Override
                public void run(String result, RuntimeException error) {
                    // No resolution found -> stop trying to sign in
                    onGoogleSignInCompleted();
                    showErrorInDialog(result);
                }
            });
        }
    }

    private void onGoogleSignInCompleted() {
        setSignInProgress(STATE_DEFAULT);
    }

    private void onSignedOut() {
        // Update the UI to reflect that the user is signed out.
        setUiControlStates(false);
        mStatus.setText(R.string.status_signed_out);

        // Disable uploading
        RegularRoutesPipeline.setUploadEnabledState(false);
    }

    private void setUiControlStates(boolean connected) {
        // Update the user interface to reflect that the user is signed in.
        mSignInButton.setEnabled(!connected);
        mSignOutButton.setEnabled(connected);
        mRevokeButton.setEnabled(connected);

        int cancelButtonVisibility = connected ? View.GONE : View.VISIBLE;
        if (!connected)
            mContinueButton.setVisibility(View.INVISIBLE);
        mCancelButton.setVisibility(cancelButtonVisibility);

    }

    private void setSignInProgress(int state) {
        mSignInProgress = state;
        int progressVisibility = (mSignInProgress == STATE_DEFAULT) ? View.GONE : View.VISIBLE;
        mProgressView.setVisibility(progressVisibility);
    }

    private GoogleApiClient buildGoogleApiClient() {
        // When we build the GoogleApiClient we specify where connected and
        // connection failed callbacks should be returned, which Google APIs our
        // app uses and which OAuth 2.0 scopes our app requests.
        return new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Plus.API, Plus.PlusOptions.builder().build())
                .addScope(Plus.SCOPE_PLUS_PROFILE)
                .build();
    }

    private void showErrorInDialog(String msg) {
        ErrorDialogFragment errFr = ErrorDialogFragment.createInstance(msg);
        errFr.show(getFragmentManager(),TAG_ERR_DIALOG );
    }

    private boolean isSignedIn() {
        return mStorage.isUserIdAvailable();
    }

    private void close(boolean cancelled) {
        final int resultCode = cancelled ? RESULT_CANCELED : RESULT_OK;
        Intent returnIntent = new Intent();
        setResult(resultCode,returnIntent);
        finish();
    }
}



