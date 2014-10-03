package fi.aalto.trafficsense.regularroutes.util;

import android.app.Service;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * A service connection intended for a local service which uses {@link LocalBinder}.
 *
 * @param <S> service type
 */
public abstract class LocalServiceConnection<S extends Service> implements ServiceConnection {
    @SuppressWarnings("unchecked")
    @Override
    public final void onServiceConnected(ComponentName name, IBinder service) {
        onService(((LocalBinder<S>) service).getService());
    }

    @Override
    public final void onServiceDisconnected(ComponentName name) {
        // Not supposed to happen with local services
    }

    protected abstract void onService(S service);
}