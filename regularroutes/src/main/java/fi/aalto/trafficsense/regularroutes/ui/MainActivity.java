package fi.aalto.trafficsense.regularroutes.ui;

import android.app.Activity;
import android.app.Dialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import fi.aalto.trafficsense.regularroutes.R;
import fi.aalto.trafficsense.regularroutes.backend.BackendService;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.backend.RegularRoutesPipeline;
import fi.aalto.trafficsense.regularroutes.util.Callback;
import fi.aalto.trafficsense.regularroutes.util.LocalBinderServiceConnection;
import timber.log.Timber;

public class MainActivity extends Activity {

    /* Static Members */
    static final int REQUEST_CODE_RECOVER_PLAY_SERVICES = 1001;

    /* Private Members */
    private final ServiceConnection mServiceConnection = new LocalBinderServiceConnection<BackendService>() {
        @Override
        protected void onService(BackendService service) {
            mBackendService = service;
        }
    };

    private ConfigFragment mConfigFragment;
    private BackendService mBackendService;
    public BackendService getBackendService() {
        return mBackendService;
    }

    /* Overridden Methods */


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent serviceIntent = new Intent(this, BackendService.class);
        startService(serviceIntent);
        bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);

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
        if (!checkPlayServices())
            Timber.w("Google play services is not installed/up-to-date");
    }

    @Override
    protected void onDestroy() {
        unbindService(mServiceConnection);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_RECOVER_PLAY_SERVICES:
                if (resultCode == Activity.RESULT_CANCELED) {
                    showToast("In order to be able to use this application, Google Play Services must be installed.");
                    exit();
                }
                return;
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
        if (id == R.id.action_exit) {
            exit();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /* Private Methods */

    /**
     * Checks that play services is installed / up-to-date and prompts user to install it if not
     * or shows error message is it is not possible to recover
     * @return true, if play services is OK; false otherwise
     **/
    private boolean checkPlayServices() {
        final int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (status != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(status)) {
                //show dialog to provide instructions to handle the problem //

                final Activity activity = this;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        final Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, activity,
                                REQUEST_CODE_RECOVER_PLAY_SERVICES);

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

    /**
     * Send all pending data to server, stop services and exit application
     **/
    private void exit() {

        if (!RegularRoutesPipeline.flushDataQueueToServer())
            Timber.e("Failed to flush collected data to server");
        mConfigFragment.stopService();
        finish();
    }
}
