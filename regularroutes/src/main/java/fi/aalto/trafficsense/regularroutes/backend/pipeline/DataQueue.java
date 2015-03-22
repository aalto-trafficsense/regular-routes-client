package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;

import java.util.Iterator;
import java.util.Queue;

import fi.aalto.trafficsense.regularroutes.backend.parser.DataPacket;
import timber.log.Timber;

public class DataQueue implements DataCollector.Listener {
    private final Queue<DataPoint> mDeque;
    private final int flushThreshold;

    private long mNextSequence;

    public DataQueue(int maxSize, int flushThreshold) {
        this.mDeque = EvictingQueue.create(maxSize);
        this.flushThreshold = flushThreshold;
    }

    @Override
    public void onDataReady(DataPacket data) {

        DataPoint dataPoint = new DataPoint(System.currentTimeMillis(), mNextSequence++, data.getLocationData(), data.getActivityData());
        Timber.d("another datapoint ready");
        this.mDeque.add(dataPoint);
    }

    public void removeUntilSequence(long sequence) {
        Iterator<DataPoint> iter = this.mDeque.iterator();
        while (iter.hasNext()) {
            DataPoint dataPoint = iter.next();
            if (dataPoint.mSequence > sequence) {
                break;
            }
            iter.remove();
        }
    }

    public ImmutableList<DataPoint> getSnapshot() {
        return ImmutableList.copyOf(this.mDeque);
    }

    public boolean isEmpty() {
        return mDeque.isEmpty();
    }

    public int size() {
        return mDeque.size();
    }

    public boolean shouldBeFlushed() {
        return mDeque.size() >= flushThreshold;
    }
}
