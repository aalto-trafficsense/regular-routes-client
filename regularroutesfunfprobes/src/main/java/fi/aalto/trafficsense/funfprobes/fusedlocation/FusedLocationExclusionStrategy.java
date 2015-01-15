package fi.aalto.trafficsense.funfprobes.fusedlocation;

import android.location.Location;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

/**
 * Strategy is copied from funf LocationProbe to provide same data serialization outcome
 * as the builtin location probe
 **/
public class FusedLocationExclusionStrategy implements ExclusionStrategy {

    public boolean shouldSkipClass(Class<?> cls) {
        return false;
    }

    public boolean shouldSkipField(FieldAttributes f) {
        String name = f.getName();
        return (f.getDeclaringClass() == Location.class &&
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
