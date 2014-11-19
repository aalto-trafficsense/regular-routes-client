package fi.aalto.trafficsense.regularroutes;

import android.app.Activity;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.widget.TextView;
import fi.aalto.trafficsense.regularroutes.backend.BackendService;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.util.LocalBinderServiceConnection;

public class DebugActivity extends Activity {
    private final ServiceConnection mServiceConnection = new LocalBinderServiceConnection<BackendService>() {
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
}
