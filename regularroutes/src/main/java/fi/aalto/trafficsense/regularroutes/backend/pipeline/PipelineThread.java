package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.IJsonObject;
import com.google.gson.JsonElement;

import edu.mit.media.funf.action.ActionAdapter;
import edu.mit.media.funf.action.RunArchiveAction;
import edu.mit.media.funf.action.RunUpdateAction;
import edu.mit.media.funf.action.RunUploadAction;
import edu.mit.media.funf.datasource.StartableDataSource;
import edu.mit.media.funf.probe.Probe;
import fi.aalto.trafficsense.regularroutes.RegularRoutesConfig;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.backend.rest.RestClient;
import fi.aalto.trafficsense.regularroutes.util.Callback;
import timber.log.Timber;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class PipelineThread {
    private final Looper mLooper;

    // getter for Pipeline to get Handler needed for instantiation of archive, update and upload actions
    public Handler getHandler() {
        return mHandler;
    }

    private final Handler mHandler;
    private final Probe.DataListener mDataListener;
    private final DataCollector mDataCollector;
    private final DataQueue mDataQueue;
    private final RestClient mRestClient;
    private final RegularRoutesConfig mConfig;
    private final Object uploadLock = new Object();

    private ImmutableCollection<StartableDataSource> mDataSources = ImmutableList.of();
    private ImmutableMap<String, StartableDataSource> mSchedules = ImmutableMap.of();

    // Archive, upload and update Configurables passed from BasicPipeline. Not very nice ...
    public PipelineThread(RegularRoutesConfig config, Context context, Looper looper, SQLiteOpenHelper databaseHelper) {
        this.mLooper = looper;
        this.mHandler = new Handler(looper);
        this.mConfig = config;

        this.mDataListener = new Probe.DataListener() {
            @Override
            public void onDataReceived(final IJsonObject probeConfig, final IJsonObject data) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mDataCollector.onDataReceived(probeConfig, data);
                    }
                });
            }

            @Override
            public void onDataCompleted(final IJsonObject probeConfig, final JsonElement checkpoint) {

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        /**
                         * Only one data completed operation (or force flush operation) access
                         * rest client at a time.
                         *
                         * This procedure skips upload attempt if other upload operation is ongoing
                         * and the next packet may then have more than the number of items set in
                         * 'flush limit' configuration.
                         *
                         * Nothing is uploaded while uploading is disabled.
                         **/
                        synchronized (uploadLock) {
                            mDataCollector.onDataCompleted(probeConfig, checkpoint);
                            if (mDataQueue.shouldBeFlushed()
                                    && mRestClient.isUploadEnabled()
                                    && !mRestClient.isUploading()) {
                                mRestClient.uploadData(mDataQueue);
                            }
                        }

                    }
                });
            }
        };
        this.mDataQueue = mConfig.createDataQueue();
        this.mDataCollector = new DataCollector(this.mDataQueue, databaseHelper);
        this.mRestClient = mConfig.createRestClient(BackendStorage.create(context), this.mHandler);

    }

    /**
     * Try sending all data in data queue to server and wait for it to finish.
     * @return false if failed to trigger data transfer or if worker thread was interrupted
     **/
    public boolean forceFlushDataToServer() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Boolean> interruptedState = new AtomicReference<>(Boolean.valueOf(false));
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    /**
                     * Only one data completed operation (or force flush operation) access
                     * rest client at a time.
                     *
                     * This procedure waits until previous data upload operations are completed
                     * and then triggers the upload
                     **/
                    synchronized (uploadLock) {

                        if (mRestClient.isUploadEnabled()) {
                            Timber.d("force flushing data to server: " + mDataQueue.size()
                                    + " items queued");
                            mRestClient.waitAndUploadData(mDataQueue);
                        }
                        else {
                            Timber.d("upload data to server is disabled: " + mDataQueue.size()
                                    + " items in queue was not uploaded");
                        }

                    }

                }
                catch (InterruptedException intEx) {
                    interruptedState.set(true);
                }
                finally {
                    latch.countDown();
                }

            }
        };
        if (!mHandler.postAtFrontOfQueue(task))
            return false;

        latch.await();
        mRestClient.waitTillNotUploading();

        if (mRestClient.isUploadEnabled())
            Timber.d("forceFlushDataToServer: completed");
        else
            Timber.d("forceFlushDataToServer: aborted (uploading disabled)");
        return !interruptedState.get();


    }

    /**
     * Trigger fetching device id from server
     **/
    public void fetchDeviceId(final Callback<Integer> callback) {
        mRestClient.fetchDeviceId(new Callback<Integer>() {
            @Override
            public void run(Integer result, RuntimeException error) {
                callback.run(result, error);
            }
        });
    }

    public RegularRoutesConfig getConfig() {
        return mConfig;
    }

    public boolean destroy() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                destroyInternal();
                latch.countDown();
            }
        };
        if (!mHandler.postAtFrontOfQueue(task))
            return false;
        latch.await();
        return true;
    }

    public void setUploadEnabledState(boolean enabled) {
        mRestClient.setUploadEnabledState(enabled);
    }

    public boolean getUploadEnabledState() {
        return mRestClient.isUploadEnabled();
    }

    public boolean getUploadingState() {
        return mRestClient.isUploading();
    }

    private void destroyInternal() {
        mRestClient.destroy();
        destroyDataSourcesAndSchedules();
        mLooper.quit();
    }

    public void configureDataSources(final ImmutableCollection<StartableDataSource> dataSources) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                configureDataSourcesInternal(dataSources);
            }
        });
    }

    private void configureDataSourcesInternal(ImmutableCollection<StartableDataSource> dataSources) {
        mDataSources = dataSources;

        for (StartableDataSource dataSource : mDataSources) {
            dataSource.setListener(mDataListener);
            dataSource.start();
        }
        Timber.i("Configured %d data sources", mDataSources.size());

    }

    public void configureSchedules(final ImmutableMap<String, StartableDataSource> schedules, final RunArchiveAction archiveAction, final RunUploadAction uploadAction, final RunUpdateAction updateAction) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                configureSchedulesInternal(schedules, archiveAction, uploadAction, updateAction);
            }
        });
    }

    private void configureSchedulesInternal(ImmutableMap<String, StartableDataSource> schedules, final RunArchiveAction archiveAction, final RunUploadAction uploadAction, final RunUpdateAction updateAction) {
        mSchedules = schedules;

        if (mSchedules.containsKey("archive")) {
            Probe.DataListener archiveListener = new ActionAdapter(archiveAction);
            mSchedules.get("archive").setListener(archiveListener);
            mSchedules.get("archive").start();
        }

        if (mSchedules.containsKey("upload")) {
            Probe.DataListener uploadListener = new ActionAdapter(uploadAction);
            mSchedules.get("upload").setListener(uploadListener);
            mSchedules.get("upload").start();
        }

        if (mSchedules.containsKey("update")) {
            Probe.DataListener updateListener = new ActionAdapter(updateAction);
            mSchedules.get("update").setListener(updateListener);
            mSchedules.get("update").start();
        }

        Timber.i("Configured %d scheduled actions", mSchedules.size());

    }



    private void destroyDataSourcesAndSchedules() {
        for (StartableDataSource dataSource : mDataSources) {
            dataSource.stop();
        }
        mDataSources = ImmutableList.of();
        for(StartableDataSource dataSource: mSchedules.values()) dataSource.stop();
        mSchedules = ImmutableMap.of();
    }

}