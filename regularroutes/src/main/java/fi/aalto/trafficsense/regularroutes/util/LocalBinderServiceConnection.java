package fi.aalto.trafficsense.regularroutes.util;

import android.app.Service;
import android.os.IBinder;

/**
 * A service connection intended for a local service which uses {@link LocalBinder}.
 *
 * @param <S> service type
 */
public abstract class LocalBinderServiceConnection<S extends Service> extends LocalServiceConnection<S> {
    @SuppressWarnings("unchecked")
    @Override
    protected final S getService(IBinder binder) {
        return ((LocalBinder<S>)binder).getService();
    }
}