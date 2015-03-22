package fi.aalto.trafficsense.regularroutes.backend.rest.types;


import com.google.gson.annotations.SerializedName;

public class AuthenticateRequest {
    @SerializedName("userId")
    public final String UserId;

    @SerializedName("installationId")
    public final String InstallationId;

    @SerializedName("deviceId")
    public final String DeviceId;

    public AuthenticateRequest(String userId, String deviceId, String installationId) {
        UserId = userId;
        InstallationId = installationId;
        DeviceId = deviceId;
    }
}
