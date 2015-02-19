package fi.aalto.trafficsense.funfprobes.activityrecognition;


import com.google.android.gms.location.DetectedActivity;

public enum ActivityType {
        /* The following order is same as defined in DetectedActivity's constants, values: 0..9 */
        IN_VEHICLE, ON_BICYCLE, ON_FOOT, RUNNING, STILL, TILTING, UNKNOWN, WALKING;

        public static ActivityType getActivityTypeByReference(final int activityTypeReference) {
            switch (activityTypeReference) {
                case DetectedActivity.IN_VEHICLE:
                    return ActivityType.IN_VEHICLE;
                case DetectedActivity.ON_BICYCLE:
                    return ActivityType.ON_BICYCLE;
                case DetectedActivity.ON_FOOT:
                    return ActivityType.ON_FOOT;
                case DetectedActivity.RUNNING:
                    return ActivityType.RUNNING;
                case DetectedActivity.STILL:
                    return ActivityType.STILL;
                case DetectedActivity.TILTING:
                    return ActivityType.TILTING;
                case DetectedActivity.UNKNOWN:
                    return ActivityType.UNKNOWN;
                case DetectedActivity.WALKING:
                    return ActivityType.WALKING;
                default:
                    return ActivityType.UNKNOWN;
            }
        }

    public static int getActivityTypeAsInteger(final ActivityType activityType) {
        switch (activityType) {
            case IN_VEHICLE:
                return DetectedActivity.IN_VEHICLE;
            case ON_BICYCLE:
                return DetectedActivity.ON_BICYCLE;
            case ON_FOOT:
                return DetectedActivity.ON_FOOT;
            case RUNNING:
                return DetectedActivity.RUNNING;
            case STILL:
                return DetectedActivity.STILL;
            case TILTING:
                return DetectedActivity.TILTING;
            case UNKNOWN:
                return DetectedActivity.UNKNOWN;
            case WALKING:
                return DetectedActivity.WALKING;
            default:
                return DetectedActivity.UNKNOWN;
        }
    }

        public static String getActivityTypeStringByReference(final int activityTypeReference) {
            return getActivityTypeByReference(activityTypeReference).name();
        }

}
