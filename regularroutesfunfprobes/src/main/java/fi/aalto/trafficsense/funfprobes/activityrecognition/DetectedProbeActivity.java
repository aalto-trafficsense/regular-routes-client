package fi.aalto.trafficsense.funfprobes.activityrecognition;

import com.google.android.gms.location.DetectedActivity;
import com.google.gson.annotations.SerializedName;


public class DetectedProbeActivity {

    /* Public Members */
    @SerializedName("activityType")
    public ActivityType Type;

    @SerializedName("confidence")
    public int Confidence;

    /* Constructor(s) */
    public DetectedProbeActivity(DetectedActivity detectedActivity) {
        this(detectedActivity.getType(), detectedActivity.getConfidence());
    }
    public DetectedProbeActivity(int activityType, int confidence) {

        this.Type = ActivityType.getActivityTypeByReference(activityType);
        this.Confidence = confidence;
    }

    /**
     * Return text representation based on confidence
     **/
    public String getConfidenceLevelAsString() {
        /**
         *  Doc: http://developer.android.com/reference/com/google/android/gms/location/DetectedActivity.html
         *  Documentation states that confidence [0, 100] less than equal to 50 is bad and
         *  greater or equal to 75 is good (likely to be true);
         **/
        String confidenceStr;
        if (Confidence <= 50)
            confidenceStr = "NOT GOOD";
        else if (Confidence <= 75)
            confidenceStr = "OK";
        else if (Confidence < 90)
            confidenceStr = "GOOD";
        else
            confidenceStr = "EXCELLENT";

        return confidenceStr;
    }

    public boolean equals(DetectedProbeActivity other) {
        return other != null
                && this.Type.equals(other.Type)
                && this.Confidence == other.Confidence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DetectedProbeActivity that = (DetectedProbeActivity) o;
        return (equals(that));
    }

    @Override
    public int hashCode() {
        int result;
        long temp = (long) ActivityType.getActivityTypeAsInteger(Type);
        result = (int) (temp ^ (temp >>> 32));
        temp = (long) Confidence;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    public String asString() {
        return Type.name();
    }

    @Override
    public String toString() {
        return asString();
    }
}
