package fi.aalto.trafficsense.funfprobes.activityrecognition;

import com.google.android.gms.location.DetectedActivity;


public class DetectedProbeActivity extends DetectedActivity {

    /* Private Members */
    private ActivityType mActivityType;

    /* Constructor(s) */
    public DetectedProbeActivity(DetectedActivity detectedActivity) {
        this(detectedActivity.getType(), detectedActivity.getConfidence());
    }
    public DetectedProbeActivity(int activityType, int confidence) {
        super(activityType, confidence);
        final int type = activityType;
        mActivityType = ActivityType.getActivityTypeByReference(type);
    }

    public ActivityType getActivityType() {
        return mActivityType;
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
        final int confidence = getConfidence();
        if (confidence <= 50)
            confidenceStr = "NOT GOOD";
        else if (confidence <= 75)
            confidenceStr = "OK";
        else if (confidence < 90)
            confidenceStr = "GOOD";
        else
            confidenceStr = "EXCELLENT";

        return confidenceStr;
    }

    public String asString() {
        return mActivityType.name();
    }
}
