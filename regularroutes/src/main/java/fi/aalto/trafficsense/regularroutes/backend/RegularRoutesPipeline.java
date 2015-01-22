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
import timber.log.Timber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class RegularRoutesPipeline implements Pipeline {
    private final AtomicReference<PipelineThread> mThread = Atomics.newReference();

    @Configurable
    public List<StartableDataSource> data = new ArrayList<StartableDataSource>();

    @Override
    public void onCreate(FunfManager manager) {
        RegularRoutesConfig config = ((RegularRoutesApplication) manager.getApplication()).getConfig();

        if (mThread.get() == null) {
            HandlerThread handlerThread = new HandlerThread(PipelineThread.class.getSimpleName());
            handlerThread.start();

            PipelineThread thread = new PipelineThread(config, manager, handlerThread.getLooper());
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
}
