package fi.aalto.trafficsense.regularroutes.ui;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import fi.aalto.trafficsense.regularroutes.R;
import fi.aalto.trafficsense.regularroutes.backend.BackendService;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.backend.RegularRoutesPipeline;
import fi.aalto.trafficsense.regularroutes.util.LocalBinderServiceConnection;
import fi.aalto.trafficsense.regularroutes.util.PlayServiceHelper;
import timber.log.Timber;

public class MainActivity extends Activity {

    public static final int RC_SIGN_IN = 1100;

    /* Private Members */
    private final ServiceConnection mServiceConnection = new LocalBinderServiceConnection<BackendService>() {
        @Override
        protected void onService(BackendService service) {
            mBackendService = service;
        }
    };

    private ConfigFragment mConfigFragment;
    private BackendService mBackendService;
    private PlayServiceHelper mPlayServiceHelper;
    private BackendStorage mStorage;
    private boolean mOpenSignInActivityIfRequired;

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    //Strat another Activity Here

                default:
                    break;
            }
            return false;
        }
    });

    /* Get Methods */
    public BackendService getBackendService() {
        return mBackendService;
    }

    /* Overridden Methods */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mOpenSignInActivityIfRequired = true;
        Intent serviceIntent = new Intent(this, BackendService.class);
        startService(serviceIntent);
        bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);



        mPlayServiceHelper = new PlayServiceHelper(this);
        mStorage = BackendStorage.create(this);
        mConfigFragment = new ConfigFragment();
        FragmentManager fm = getFragmentManager();
        FragmentTransaction trans = fm.beginTransaction();
        trans.replace(R.id.main_configFragmentArea, mConfigFragment).commit();

    }

    @Override
    protected void onResume() {
        super.onResume();

        /**
         * Play Services is required by fused location / activity recognition probe and is therefore
         * mandatory to use this client. Therefore it is verified that correct version of play
         * services is installed when application starts.
         **/
        if (!mPlayServiceHelper.checkPlayServiceAvailability())
            Timber.w("Google play services is not installed/up-to-date");

        if (mOpenSignInActivityIfRequired && !isSignedIn()) {

            mOpenSignInActivityIfRequired = false;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showLoginActivity();
                }
            }, 1000);

        }
    }

    @Override
    protected void onDestroy() {
        unbindService(mServiceConnection);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PlayServiceHelper.RC_RECOVER_PLAY_SERVICES:
                if (resultCode == Activity.RESULT_CANCELED) {
                    showToast("In order to be able to use this application, Google Play Services must be installed.");
                    exit();
                }
                return;
            case RC_SIGN_IN:
                if (resultCode != RESULT_OK) {
                    Timber.i("Sign in cancelled");

                }
                else
                    Timber.i("Sign in OK");
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_exit:
                exit();
                return true;
            case R.id.action_signIn:
                showLoginActivity();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /* Private Methods */
    private void showToast(String msg) {
        if (msg == null)
            return;


        final String messageText = msg;
        final Activity activity = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, messageText, Toast.LENGTH_SHORT).show();
            }
        });

    }

    public boolean isSignedIn() {
        return mStorage.isUserIdAvailable();
    }

    private void showLoginActivity() {

        Intent intent = new Intent(this, LoginActivity.class);
        startActivityForResult(intent, RC_SIGN_IN);
    }

    /**
     * Send all pending data to server, stop services and exit application
     **/
    public void exit() {

        if (!RegularRoutesPipeline.flushDataQueueToServer())
            Timber.e("Failed to flush collected data to server");
        mConfigFragment.stopService();
        finish();
    }
}
