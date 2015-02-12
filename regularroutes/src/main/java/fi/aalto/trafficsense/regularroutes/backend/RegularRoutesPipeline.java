package fi.aalto.trafficsense.regularroutes.backend;

import android.database.sqlite.SQLiteOpenHelper;
import android.os.HandlerThread;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Atomics;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import edu.mit.media.funf.FunfManager;
import edu.mit.media.funf.action.RunArchiveAction;
import edu.mit.media.funf.action.RunUpdateAction;
import edu.mit.media.funf.action.RunUploadAction;
import edu.mit.media.funf.config.ConfigUpdater;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.datasource.StartableDataSource;
import edu.mit.media.funf.pipeline.Pipeline;
import edu.mit.media.funf.storage.DefaultArchive;
import edu.mit.media.funf.storage.FileArchive;
import edu.mit.media.funf.storage.NameValueDatabaseHelper;
import edu.mit.media.funf.storage.RemoteFileArchive;
import edu.mit.media.funf.storage.UploadService;
import edu.mit.media.funf.util.StringUtil;
import fi.aalto.trafficsense.regularroutes.RegularRoutesApplication;
import fi.aalto.trafficsense.regularroutes.RegularRoutesConfig;
import fi.aalto.trafficsense.regularroutes.backend.pipeline.PipelineThread;
import fi.aalto.trafficsense.regularroutes.util.Callback;
import timber.log.Timber;
import java.util.HashMap;
import java.util.Map;

public class RegularRoutesPipeline implements Pipeline {
    private final AtomicReference<PipelineThread> mThread = Atomics.newReference();
    private RegularRoutesConfig mConfig;

    // archive, upload, and update related @Configurables and variables similar to Funf BasicPipeline
    @Configurable
    public String name = "default";

    @Configurable
    public int version = 1;

    @Configurable
    public FileArchive archive = null;

    @Configurable
    public RemoteFileArchive upload = null;

    @Configurable
    public ConfigUpdater update = null;

    private UploadService mUploader;
    private RunArchiveAction mArchiveAction;
    private RunUploadAction mUploadAction;
    private RunUpdateAction mUpdateAction;
    private SQLiteOpenHelper mDatabaseHelper;

    @Configurable
    public Map<String, StartableDataSource> schedules = new HashMap<String, StartableDataSource>();

    @Configurable
    public List<StartableDataSource> data = new ArrayList<StartableDataSource>();

    @Override
    public void onCreate(FunfManager manager) {

        mConfig = ((RegularRoutesApplication) manager.getApplication()).getConfig();
        mDatabaseHelper = new NameValueDatabaseHelper(manager, StringUtil.simpleFilesafe(name), version);

        if (mThread.get() == null) {
            HandlerThread handlerThread = new HandlerThread(PipelineThread.class.getSimpleName());
            handlerThread.start();

            PipelineThread thread;
            try {
                thread = PipelineThread.create(mConfig, manager, handlerThread, mDatabaseHelper).get();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
            mThread.set(thread);
            sPipeline.set(thread);

            thread.configureDataSources(ImmutableList.copyOf(data));

            // instantiation of archive, upload and update actions similarly as in BasicPipeline.onCreate
            mUploader = new UploadService(manager);
            mUploader.start();
            if (archive == null) {
                archive = new DefaultArchive(manager, name);
            }
            mArchiveAction = new RunArchiveAction(archive, mDatabaseHelper);
            mArchiveAction.setHandler(thread.getHandler());
            mUploadAction = new RunUploadAction(archive, upload, mUploader);
            mUploadAction.setHandler(thread.getHandler());
            mUpdateAction = new RunUpdateAction(name, manager, update);
            mUpdateAction.setHandler(thread.getHandler());

            thread.configureSchedules(ImmutableMap.copyOf(schedules), mArchiveAction, mUploadAction, mUpdateAction);
        }

    }

    @Override
    public void onRun(String action, JsonElement config) {
        Timber.d(String.format("onRun(%s, %s)", action, config));
    }

    @Override
    public void onDestroy() {
        if (mUploader != null) {
            mUploader.stop();
        }
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
     *
     * @param callback callback that gets executed when the value is ready (or null in error case)
     */
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
     *
     * @return true, if uploading is ongoing
     */
    public static boolean isUploading() {
        PipelineThread pipeline = sPipeline.get();
        if (pipeline == null)
            return false;

        return pipeline.getUploadingState();
    }

    /**
     * Get enabled state of upload procedure
     *
     * @return true, if uploading is enabled
     */
    public static boolean isUploadEnabled() {
        PipelineThread pipeline = sPipeline.get();
        if (pipeline == null)
            return false;

        return pipeline.getUploadEnabledState();
    }

    /**
     * Set enabled state of upload procedure
     *
     * @return true, if state was changed successfully
     */
    public static boolean setUploadEnabledState(boolean enabled) {
        PipelineThread pipeline = sPipeline.get();
        if (pipeline == null)
            return false;

        pipeline.setUploadEnabledState(enabled);
        return true;
    }

    /**
     * Get config used in pipeline
     */
    public static RegularRoutesConfig getConfig() {
        PipelineThread pipeline = sPipeline.get();
        if (pipeline == null)
            return null;

        return pipeline.getConfig();
    }
}
