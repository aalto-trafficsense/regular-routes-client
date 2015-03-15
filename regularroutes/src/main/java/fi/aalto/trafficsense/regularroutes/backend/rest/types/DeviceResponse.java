package fi.aalto.trafficsense.regularroutes.backend.rest.types;

import com.google.gson.annotations.SerializedName;

public class DeviceResponse {
    @SerializedName("deviceId")
    public final String DeviceId;

    @SerializedName(("sessionToken"))
    public final String mSessionToken;

    @SerializedName(("error"))
    public final String mError;


    public DeviceResponse(String deviceId, String  deviceToken) {
        this.DeviceId = deviceId;
        this.mSessionToken = deviceToken;
        this.mError = null;
    }

    public DeviceResponse(String error) {
        this.DeviceId = null;
        this.mSessionToken = null;
        this.mError = error;
    }
}