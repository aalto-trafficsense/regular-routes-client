package fi.aalto.trafficsense.regularroutes.backend.rest.types;

import com.google.gson.annotations.SerializedName;

/**
 * Register response returns device token that should be attached to each rest api call
 **/
public class RegisterResponse {
    @SerializedName("sessionToken")
    public final String mSessionToken;

    public RegisterResponse(String sessionToken) {
        this.mSessionToken = sessionToken;
    }
}
