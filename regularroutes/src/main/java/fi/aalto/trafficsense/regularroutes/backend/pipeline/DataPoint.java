package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import com.google.gson.annotations.SerializedName;

import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityDataContainer;
import fi.aalto.trafficsense.regularroutes.backend.parser.LocationData;

public class DataPoint {
    @SerializedName("time")
    public final long mTime;
    @SerializedName("location")
    public final LocationData mLocation;
    @SerializedName("activityData")
    public final ActivityDataContainer mActivityData;
    public transient final long mSequence;

    public DataPoint(long time, long sequence, LocationData location, ActivityDataContainer mActivities) {
        this.mTime = time;
        this.mSequence = sequence;
        this.mLocation = location;
        this.mActivityData = mActivities;
    }

    @Override
    public String toString() {
        final String activities = mActivityData != null ? mActivityData.toString() : "[]";
        return String.format("DataPoint{mTime=%d, mSequence=%d, mLocation=%s, mActivities=%s}", mTime, mSequence, mLocation, activities);
    }
}
