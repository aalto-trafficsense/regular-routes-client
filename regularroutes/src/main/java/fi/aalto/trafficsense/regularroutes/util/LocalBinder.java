package fi.aalto.trafficsense.regularroutes.util;

import android.app.Service;
import android.os.Binder;

/**
 * A binder that wraps a local service.
 *
 * @param <S> service type
 */
public final class LocalBinder<S extends Service> extends Binder {
    private final S mService;

    public LocalBinder(S service) {
        mService = service;
    }

    public S getService() {
        return mService;
    }
}
