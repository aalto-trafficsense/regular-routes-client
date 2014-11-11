package fi.aalto.trafficsense.regularroutes.backend.rest.types;

import com.google.gson.annotations.SerializedName;

public class AuthenticateResponse {
    @SerializedName("sessionId")
    public final String mSessionId;

    public AuthenticateResponse(String mSessionId) {
        this.mSessionId = mSessionId;
    }
}
