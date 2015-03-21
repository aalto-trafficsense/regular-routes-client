package fi.aalto.trafficsense.regularroutes.backend.rest.types;


import com.google.gson.annotations.SerializedName;

public class AuthenticateRequest {
    @SerializedName("deviceAuthId")
    public final String DeviceAuthId;

    public AuthenticateRequest(String deviceAuthId) {
        this.DeviceAuthId = deviceAuthId;
    }
}
