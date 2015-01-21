package fi.aalto.trafficsense.funfprobes.activityrecognition;


import com.google.android.gms.location.DetectedActivity;

public enum ActivityType {
        IN_VEHICLE, ON_BICYCLE, ON_FOOT, STILL, TILTING, UNKNOWN;

        public static ActivityType getActivityTypeByReference(final int activityTypeReference) {
            switch (activityTypeReference) {
                case DetectedActivity.IN_VEHICLE:
                    return ActivityType.IN_VEHICLE;
                case DetectedActivity.ON_BICYCLE:
                    return ActivityType.ON_BICYCLE;
                case DetectedActivity.ON_FOOT:
                    return ActivityType.ON_FOOT;
                case DetectedActivity.STILL:
                    return ActivityType.STILL;
                case DetectedActivity.TILTING:
                    return ActivityType.TILTING;
                case DetectedActivity.UNKNOWN:
                default:
                    return ActivityType.UNKNOWN;
            }
        }

        public static String getActivityTypeStringByReference(final int activityTypeReference) {
            return getActivityTypeByReference(activityTypeReference).name();
        }

}
