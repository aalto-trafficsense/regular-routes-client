package fi.aalto.trafficsense.regularroutes.util;

import android.os.Handler;

import java.util.concurrent.Executor;

public class HandlerExecutor implements Executor {
    private final Handler mHandler;

    public HandlerExecutor(Handler handler) {
        this.mHandler = handler;
    }

    @Override
    public void execute(Runnable command) {
        mHandler.post(command);
    }
}
