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
import timber.log.Timber;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class PipelineThread {
    private final Looper mLooper;
    private final Handler mHandler;
    private final Probe.DataListener mDataListener;
    private final DataCollector mDataCollector;
    private final DataQueue mDataQueue;
    private final RestClient mRestClient;
    private final Object uploadLock = new Object();

    private ImmutableCollection<StartableDataSource> mDataSources = ImmutableList.of();

    public PipelineThread(RegularRoutesConfig config, Context context, Looper looper) {
        this.mLooper = looper;
        this.mHandler = new Handler(looper);
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
                        synchronized (uploadLock) {
                            mDataCollector.onDataCompleted(probeConfig, checkpoint);
                            if (mDataQueue.shouldBeFlushed() && !mRestClient.isUploading()) {
                                mRestClient.uploadData(mDataQueue);
                                uploadLock.notify();
                            }
                        }

                    }
                });
            }
        };
        this.mDataQueue = config.createDataQueue();
        this.mDataCollector = new DataCollector(this.mDataQueue);
        this.mRestClient = config.createRestClient(BackendStorage.create(context), this.mHandler);
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
                    synchronized (uploadLock) {
                        while(mRestClient.isUploading()) {
                            uploadLock.wait();
                        }
                        Timber.d("force flushing data to server: " + mDataQueue.size() + " items queued");
                        mRestClient.uploadData(mDataQueue);

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
        while(!interruptedState.get() && mRestClient.isUploading());
        Timber.d("forceFlushDataToServer: completed");
        return !interruptedState.get();


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