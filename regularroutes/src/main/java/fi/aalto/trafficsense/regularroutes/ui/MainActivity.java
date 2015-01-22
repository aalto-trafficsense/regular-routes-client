package fi.aalto.trafficsense.regularroutes.ui;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import fi.aalto.trafficsense.regularroutes.R;
import fi.aalto.trafficsense.regularroutes.backend.BackendService;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.backend.RegularRoutesPipeline;
import fi.aalto.trafficsense.regularroutes.util.LocalBinderServiceConnection;
import timber.log.Timber;

public class MainActivity extends Activity {
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

        BackendStorage storage = BackendStorage.create(this);
        TextView deviceToken = (TextView) findViewById(R.id.device_token);
        deviceToken.setText(storage.readDeviceToken().or("?"));
    }

    @Override
    protected void onDestroy() {
        unbindService(mServiceConnection);
        super.onDestroy();
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
     * Send all pending data to server, stop services and exit application
     **/
    private void exit() {

        if (!RegularRoutesPipeline.flushDataQueueToServer())
            Timber.e("Failed to flush collected data to server");
        mConfigFragment.stopService();
        finish();
    }
}
