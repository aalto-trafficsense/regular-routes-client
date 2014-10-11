package fi.aalto.trafficsense.regularroutes.util;

import android.app.Service;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import timber.log.Timber;

public abstract class LocalServiceConnection<S extends Service> implements ServiceConnection {
    @Override
    public final void onServiceConnected(ComponentName name, IBinder service) {
        Timber.d("Connected to service %s", name);
        onService(getService(service));
    }

    @Override
    public final void onServiceDisconnected(ComponentName name) {
        // Not supposed to happen with local services
        Timber.e("Unexpected disconnection from local service %s", name);
    }

    protected abstract S getService(IBinder binder);

    protected abstract void onService(S service);
}
