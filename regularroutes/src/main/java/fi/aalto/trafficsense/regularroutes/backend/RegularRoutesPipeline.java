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
            sPipeline.set(thread);

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

    private static final AtomicReference<PipelineThread> sPipeline = Atomics.newReference();

    public static boolean flushDataQueueToServer() {
        PipelineThread pipeline = sPipeline.get();
        if (pipeline == null)
            return false;

        try {
            pipeline.forceFlushDataToServer();
        } catch (Exception ex) {
            Timber.e("Failed to flush data queue to server: " + ex.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Fetch device id from the server
     * @param callback callback that gets executed when the value is ready (or null in error case)
     **/
    public static void fetchDeviceId(Callback<Integer> callback) {
        PipelineThread pipeline = sPipeline.get();
        if (pipeline == null)
            callback.run(null, new RuntimeException("Pipeline is not initialized"));
        else {
            pipeline.fetchDeviceId(callback);
        }
    }

    /**
     * Get state if pipeline is uploading data or not
     * @return true, if uploading is ongoing
     **/
    public static boolean isUploading() {
        PipelineThread pipeline = sPipeline.get();
        if (pipeline == null)
            return false;

        return pipeline.getUploadingState();
    }

    /**
     * Get enabled state of upload procedure
     * @return true, if uploading is enabled
     **/
    public static boolean isUploadEnabled() {
        PipelineThread pipeline = sPipeline.get();
        if (pipeline == null)
            return false;

        return pipeline.getUploadEnabledState();
    }

    /**
     * Set enabled state of upload procedure
     * @return true, if state was changed successfully
     **/
    public static boolean setUploadEnabledState(boolean enabled) {
        PipelineThread pipeline = sPipeline.get();
        if (pipeline == null)
            return false;

        pipeline.setUploadEnabledState(enabled);
        return true;
    }

    /**
     * Get config used in pipeline
     **/
    public static RegularRoutesConfig getConfig() {
        PipelineThread pipeline = sPipeline.get();
        if (pipeline == null)
            return null;

        return pipeline.getConfig();
    }
}
