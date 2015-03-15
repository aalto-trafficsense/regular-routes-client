package fi.aalto.trafficsense.regularroutes.backend.rest.types;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gson.annotations.SerializedName;

import fi.aalto.trafficsense.regularroutes.backend.pipeline.DataPoint;
import fi.aalto.trafficsense.regularroutes.backend.pipeline.DataQueue;

public class DataBody {
    @SerializedName("dataPoints")
    public final ImmutableCollection<DataPoint> mDataPoints;
    @SerializedName("sequence")
    public final long mSequence;

    public DataBody(ImmutableCollection<DataPoint> dataPoints, long sequence) {
        this.mDataPoints = dataPoints;
        this.mSequence = sequence;
    }

    public static DataBody createSnapshot(DataQueue queue) {
        ImmutableList<DataPoint> dataPoints = queue.getSnapshot();
        long sequence = Iterables.getLast(dataPoints).mSequence;
        return new DataBody(dataPoints, sequence);
    }

}
