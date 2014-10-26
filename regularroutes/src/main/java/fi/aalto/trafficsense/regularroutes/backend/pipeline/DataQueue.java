package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import fi.aalto.trafficsense.regularroutes.backend.parser.LocationData;
import timber.log.Timber;

import java.util.ArrayDeque;
import java.util.Deque;

public class DataQueue implements DataCollector.Listener {
    private final Deque<DataPoint> mDeque;

    private long mNextSequence;

    public DataQueue() {
        this.mDeque = new ArrayDeque<DataPoint>();
    }

    @Override
    public void onDataReady(LocationData locationData) {
        DataPoint dataPoint = new DataPoint(System.currentTimeMillis(), mNextSequence++, locationData);
        while (!this.mDeque.offer(dataPoint)) {
            DataPoint discarded = this.mDeque.pop();
            Timber.w("Queue full, discarding data point %s", discarded);
        }
        Timber.d("%s", this.mDeque);
    }

}
