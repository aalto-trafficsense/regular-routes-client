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

    @SerializedName("oldDeviceId")
    public String OldDeviceId; // Used to link existing accounts to authenticated account

    public RegisterRequest(String deviceAuthId, String oneTimeToken) {
        super(deviceAuthId);
        this.OneTimeToken = oneTimeToken;
        this.OldDeviceId = "";
    }

    public RegisterRequest(String deviceAuthId, String oneTimeToken, String oldDeviceId) {
        this(deviceAuthId, oneTimeToken);
        this.OldDeviceId = oldDeviceId;
    }

    public RegisterRequest(AuthenticateRequest authRequest, String oneTimeToken) {
        this(authRequest.DeviceAuthId, oneTimeToken);
    }

    public RegisterRequest(AuthenticateRequest authRequest, String oneTimeToken, String oldDeviceId) {
        this(authRequest.DeviceAuthId, oneTimeToken, oldDeviceId);
    }
}