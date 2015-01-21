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

    public String asString() {
        return mActivityType.name();
    }
}
