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

public class PipelineThread {
    private final Looper mLooper;
    private final Handler mHandler;
    private final Probe.DataListener mDataListener;
    private final DataCollector mDataCollector;
    private final DataQueue mDataQueue;
    private final RestClient mRestClient;

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
                        mDataCollector.onDataCompleted(probeConfig, checkpoint);
                        if (mDataQueue.shouldBeFlushed() && !mRestClient.isUploading()) {
                            mRestClient.uploadData(mDataQueue);
                        }
                    }
                });
            }
        };
        this.mDataQueue = config.createDataQueue();
        this.mDataCollector = new DataCollector(this.mDataQueue);
        this.mRestClient = config.createRestClient(BackendStorage.create(context), this.mHandler);
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