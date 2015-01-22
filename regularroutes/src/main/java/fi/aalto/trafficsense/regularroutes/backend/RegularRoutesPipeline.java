package fi.aalto.trafficsense.regularroutes.backend;

import android.os.HandlerThread;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Atomics;
import com.google.gson.JsonElement;
import edu.mit.media.funf.FunfManager;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.datasource.StartableDataSource;
import edu.mit.media.funf.pipeline.Pipeline;
import fi.aalto.trafficsense.regularroutes.RegularRoutesApplication;
import fi.aalto.trafficsense.regularroutes.RegularRoutesConfig;
import fi.aalto.trafficsense.regularroutes.backend.pipeline.PipelineThread;
import fi.aalto.trafficsense.regularroutes.util.Callback;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class RegularRoutesPipeline implements Pipeline {
    private final AtomicReference<PipelineThread> mThread = Atomics.newReference();
    private RegularRoutesConfig mConfig;

    @Configurable
    public List<StartableDataSource> data = new ArrayList<StartableDataSource>();

    @Override
    public void onCreate(FunfManager manager) {
        mConfig = ((RegularRoutesApplication) manager.getApplication()).getConfig();

        if (mThread.get() == null) {
            HandlerThread handlerThread = new HandlerThread(PipelineThread.class.getSimpleName());
            handlerThread.start();

            PipelineThread thread = new PipelineThread(mConfig, manager, handlerThread.getLooper());
            mThread.set(thread);
            sPipeline = mThread;

            thread.configureDataSources(ImmutableList.copyOf(data));
        }
    }

    @Override
    public void onRun(String action, JsonElement config) {
        Timber.d(String.format("onRun(%s, %s)", action, config));
    }

    @Override
    public void onDestroy() {
        PipelineThread thread = mThread.getAndSet(null);
        if (thread != null) {
            try {
                boolean result = thread.destroy();
                if (!result)
                    Timber.w("Failed to destroy PipelineThread");
            } catch (InterruptedException e) {
                Timber.e(e, "Failed to destroy PipelineThread");
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return mThread.get() != null;
    }

    private static AtomicReference<PipelineThread> sPipeline = null;
    public static boolean flushDataQueueToServer() {

        if (sPipeline == null)
            return false;
        try {
            PipelineThread thread = sPipeline.get();
            if (thread != null)
                thread.forceFlushDataToServer();

            return thread != null;
        } catch (Exception ex) {
            Timber.e("Failed to flush data queue to server: " + ex.getMessage());
            return false;
        }


    }

    /**
     * Fetch device id from the server
     * @param callback callback that gets executed when the value is ready (or null in error case)
     **/
    public static void fetchDeviceId(Callback<Integer> callback) {
        if (sPipeline == null)
            callback.run(null, new RuntimeException("Pipeline is not initialized"));
        else {
            PipelineThread pipeline = sPipeline.get();
            if (pipeline == null) {
                callback.run(null, new RuntimeException("Pipeline is not initialized"));
            }
            else {
                pipeline.fetchDeviceId(callback);
            }
        }
    }

    /**
     * Get config used in pipeline
     **/
    public static RegularRoutesConfig getConfig() {
        if (sPipeline == null)
            return null;

        PipelineThread pipeline = sPipeline.get();
        if (pipeline == null)
            return null;

        return pipeline.getConfig();
    }
}
