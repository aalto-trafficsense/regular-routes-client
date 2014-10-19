package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import android.os.Handler;
import android.os.Looper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.gson.IJsonObject;
import com.google.gson.JsonElement;
import edu.mit.media.funf.datasource.StartableDataSource;
import edu.mit.media.funf.probe.Probe;
import timber.log.Timber;

public class PipelineThread {
    private final Looper mLooper;
    private final Handler mHandler;
    private final Probe.DataListener mDataListener;

    private ImmutableCollection<StartableDataSource> mDataSources = ImmutableList.of();

    public PipelineThread(Looper looper) {
        this.mLooper = looper;
        this.mHandler = new Handler(looper);
        this.mDataListener = new Probe.DataListener() {
            @Override
            public void onDataReceived(IJsonObject probeConfig, IJsonObject data) {
                Timber.d(String.format("onDataReceived: %s, %s", probeConfig, data));
            }

            @Override
            public void onDataCompleted(IJsonObject probeConfig, JsonElement checkpoint) {
                Timber.d(String.format("onDataCompleted: %s, %s", probeConfig, checkpoint));
            }
        };
    }

    public void quit() {
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
    }
}