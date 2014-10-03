package fi.aalto.trafficsense.regularroutes;

import android.app.Activity;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import fi.aalto.trafficsense.regularroutes.backend.BackendService;
import fi.aalto.trafficsense.regularroutes.util.LocalServiceConnection;

public class DebugActivity extends Activity {
    private final ServiceConnection mServiceConnection = new LocalServiceConnection<BackendService>() {
        @Override
        protected void onService(BackendService service) {
            mBackendService = service;
        }
    };

    private BackendService mBackendService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);

        Intent serviceIntent = new Intent(this, BackendService.class);
        startService(serviceIntent);
        bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        unbindService(mServiceConnection);
        super.onDestroy();
    }
}
