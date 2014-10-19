package fi.aalto.trafficsense.regularroutes.backend;

import android.os.HandlerThread;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Atomics;
import com.google.gson.JsonElement;
import edu.mit.media.funf.FunfManager;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.datasource.StartableDataSource;
import edu.mit.media.funf.pipeline.Pipeline;
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
        if (mThread.get() == null) {
            HandlerThread handlerThread = new HandlerThread(PipelineThread.class.getSimpleName());
            handlerThread.start();

            PipelineThread thread = new PipelineThread(handlerThread.getLooper());
            mThread.set(thread);

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
            thread.quit();
        }
    }

    @Override
    public boolean isEnabled() {
        return mThread.get() != null;
    }

}