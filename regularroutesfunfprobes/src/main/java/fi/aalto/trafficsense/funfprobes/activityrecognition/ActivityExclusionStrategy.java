package fi.aalto.trafficsense.funfprobes.activityrecognition;


import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

/**
 * Strategy to filter activity recognition result from extra fields
 * as the builtin location probe
 **/
public class ActivityExclusionStrategy implements ExclusionStrategy {

    public boolean shouldSkipClass(Class<?> cls) {
        return false;
    }

    public boolean shouldSkipField(FieldAttributes f) {
        String name = f.getName();
        return (f.getDeclaringClass() == ActivityRecognitionResult.class &&
                (name.equals("mResults")
                        || name.equals("mDistance")
                        || name.equals("mInitialBearing")
                        || name.equals("mLat1")
                        || name.equals("mLat2")
                        || name.equals("mLon1")
                        || name.equals("mLon2")
                        || name.equals("mLon2")
                )
        );
    }


}
