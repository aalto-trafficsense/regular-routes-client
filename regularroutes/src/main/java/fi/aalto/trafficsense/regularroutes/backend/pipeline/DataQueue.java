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
        Timber.d("DataQueue: constructor called with maxSize: "+maxSize+" flushThreshold: "+flushThreshold);
    }

    @Override
    public void onDataReady(DataPacket data) {

        DataPoint dataPoint = new DataPoint(System.currentTimeMillis(), mNextSequence++, data.getLocationData(), data.getActivityData());
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

    // MJR: Auxiliary procedure to help cope with mysterious "500 INTERNAL SERVER ERROR"s during upload.
    // TODO: Remove method, when root cause for errors has been cleared
    public void removeOne() {
        Iterator<DataPoint> iter = this.mDeque.iterator();
        if (iter.hasNext()) iter.remove();
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
        Timber.d("DataQueue:shouldBeFlushed (test) called with size:"+mDeque.size()+" threshold "+flushThreshold);
        return mDeque.size() >= flushThreshold;
    }
}
