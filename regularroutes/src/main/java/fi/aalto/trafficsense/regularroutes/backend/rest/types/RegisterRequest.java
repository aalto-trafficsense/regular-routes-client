package fi.aalto.trafficsense.regularroutes.backend.rest.types;


import com.google.gson.annotations.SerializedName;

/**
 * RegisterRequest is used to ask server to authenticate client from Google authentication
 * with one time token and client's own id (hash).
 * Afterwards client is expected to authenticate with the same client id
 **/
public class RegisterRequest extends AuthenticateRequest {
    @SerializedName("oneTimeToken")
    public final String OneTimeToken;

    @SerializedName("deviceModel")
    public final String DeviceModel;

    public RegisterRequest(String userId, String deviceId, String installationId, String oneTimeToken, String deviceModel) {
        super(userId, deviceId, installationId);
        OneTimeToken = oneTimeToken;
        DeviceModel = deviceModel;
    }


    public RegisterRequest(AuthenticateRequest authRequest, String oneTimeToken, String deviceModel) {
        this(authRequest.UserId, authRequest.DeviceId, authRequest.InstallationId, oneTimeToken, deviceModel);
    }
}