package fi.aalto.trafficsense.regularroutes.backend.parser;

import com.google.common.base.Optional;
import com.google.gson.IJsonObject;
import com.google.gson.annotations.SerializedName;

// Review session: Model object used by parser and pipeline

public final class LocationData {
    private static final String KEY_ACCURACY = "mAccuracy";
    private static final String KEY_LATITUDE = "mLatitude";
    private static final String KEY_LONGITUDE = "mLongitude";
    private static final String KEY_TIME = "mTime";
    private static final String[] KEYS = {KEY_ACCURACY, KEY_LATITUDE, KEY_LONGITUDE, KEY_TIME};

    @SerializedName("accuracy")
    public final double mAccuracy;
    @SerializedName("latitude")
    public final double mLatitude;
    @SerializedName("longitude")
    public final double mLongitude;
    @SerializedName("time")
    public final long mTime;

    public static Optional<LocationData> parseJson(IJsonObject data) {
        for (String key : KEYS) {
            if (!data.has(key))
                return Optional.absent();
        }

        double accuracy = data.get(KEY_ACCURACY).getAsDouble();
        double latitude = data.get(KEY_LATITUDE).getAsDouble();
        double longitude = data.get(KEY_LONGITUDE).getAsDouble();
        long time = data.get(KEY_TIME).getAsLong();

        return Optional.of(new LocationData(accuracy, latitude, longitude, time));
    }

    public LocationData(double accuracy, double latitude, double longitude, long time) {
        this.mAccuracy = accuracy;
        this.mLatitude = latitude;
        this.mLongitude = longitude;
        this.mTime = time;
    }

    @Override
    public String toString() {
        return "LocationData{" +
                "mAccuracy=" + mAccuracy +
                ", mLatitude=" + mLatitude +
                ", mLongitude=" + mLongitude +
                ", mTime=" + mTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocationData that = (LocationData) o;

        if (Double.compare(that.mAccuracy, mAccuracy) != 0) return false;
        if (Double.compare(that.mLatitude, mLatitude) != 0) return false;
        if (Double.compare(that.mLongitude, mLongitude) != 0) return false;
        if (mTime != that.mTime) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(mAccuracy);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(mLatitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(mLongitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (mTime ^ (mTime >>> 32));
        return result;
    }

}
