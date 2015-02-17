package fi.aalto.trafficsense.regularroutes.backend.parser;


import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityDataContainer;

/**
 * Median data object to bind activity data with location data
 **/
public class DataPacket {
    final LocationData mLocationData;
    final ActivityDataContainer mActivityData;

    public DataPacket(LocationData locationData, ActivityDataContainer activityData) {
        this.mLocationData = locationData;
        this.mActivityData = activityData;
    }

    public LocationData getLocationData() {
        return mLocationData;
    }

    public ActivityDataContainer getActivityData() {
        return mActivityData;
    }
}
