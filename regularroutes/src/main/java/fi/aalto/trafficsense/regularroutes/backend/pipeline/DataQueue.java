package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import com.google.common.collect.EvictingQueue;
import fi.aalto.trafficsense.regularroutes.backend.parser.LocationData;
import timber.log.Timber;

import java.util.Queue;

public class DataQueue implements DataCollector.Listener {
    private static final int MAX_SIZE = 256;
    private final Queue<DataPoint> mDeque;

    private long mNextSequence;

    public DataQueue() {
        this.mDeque = EvictingQueue.create(MAX_SIZE);
    }

    @Override
    public void onDataReady(LocationData locationData) {
        DataPoint dataPoint = new DataPoint(System.currentTimeMillis(), mNextSequence++, locationData);
        this.mDeque.add(dataPoint);
        Timber.d("%s", this.mDeque);
    }

}
