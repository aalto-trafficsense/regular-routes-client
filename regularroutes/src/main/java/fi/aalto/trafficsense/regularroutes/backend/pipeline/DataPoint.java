package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import fi.aalto.trafficsense.regularroutes.backend.parser.LocationData;

public class DataPoint {
    public final long mTime;
    public final long mSequence;
    public final LocationData mLocation;

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
