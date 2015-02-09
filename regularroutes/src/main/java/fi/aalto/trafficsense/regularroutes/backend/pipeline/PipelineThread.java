package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.gson.IJsonObject;
import com.google.gson.JsonElement;
import edu.mit.media.funf.datasource.StartableDataSource;
import edu.mit.media.funf.probe.Probe;
import fi.aalto.trafficsense.regularroutes.RegularRoutesConfig;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.backend.rest.RestClient;
import fi.aalto.trafficsense.regularroutes.util.Callback;
import timber.log.Timber;

import java.util.Dictionary;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * All pipeline operations should be executed in a single PipelineThread. The thread uses a
 * Looper, so work can be pushed to the thread with a Handler connected to the Looper.
 *
 * All work outside Runnables pushed to the Handler must be thread-safe.
 */
public class PipelineThread {
    private final Looper mLooper;
    private final Handler mHandler;
    private final Probe.DataListener mDataListener;
    private final DataCollector mDataCollector;
    private final DataQueue mDataQueue;
    private final RestClient mRestClient;
    private final RegularRoutesConfig mConfig;

    private ImmutableCollection<StartableDataSource> mDataSources = ImmutableList.of();

    public PipelineThread(RegularRoutesConfig config, Context context, Looper looper) {
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
                        mDataCollector.onDataCompleted(probeConfig, checkpoint);
                        if (mDataQueue.shouldBeFlushed()
                                && mRestClient.isUploadEnabled()
                                && !mRestClient.isUploading()) {
                            mRestClient.uploadData(mDataQueue);
                        }
                    }
                });
            }
        };
        this.mDataQueue = mConfig.createDataQueue();
        this.mDataCollector = new DataCollector(this.mDataQueue);
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
                    if (mRestClient.isUploadEnabled()) {
                        Timber.d("force flushing data to server: " + mDataQueue.size()
                                + " items queued");
                        mRestClient.waitAndUploadData(mDataQueue);
                    } else {
                        Timber.d("upload data to server is disabled: " + mDataQueue.size()
                                + " items in queue was not uploaded");
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
        destroyDataSources();
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

    private void destroyDataSources() {
        for (StartableDataSource dataSource : mDataSources) {
            dataSource.stop();
        }
        mDataSources = ImmutableList.of();
    }

}