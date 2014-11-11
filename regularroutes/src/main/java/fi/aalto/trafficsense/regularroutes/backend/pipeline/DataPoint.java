package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import com.google.gson.annotations.SerializedName;
import fi.aalto.trafficsense.regularroutes.backend.parser.LocationData;

public class DataPoint {
    @SerializedName("time")
    public final long mTime;
    @SerializedName("location")
    public final LocationData mLocation;
    public transient final long mSequence;

    public DataPoint(long time, long sequence, LocationData location) {
        this.mTime = time;
        this.mSequence = sequence;
        this.mLocation = location;
    }

    @Override
    public String toString() {
        return "DataPoint{" +
                "mTime=" + mTime +
                ", mSequence=" + mSequence +
                ", mLocation=" + mLocation +
                '}';
    }
}
