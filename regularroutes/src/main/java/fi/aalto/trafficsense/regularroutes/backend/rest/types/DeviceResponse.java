package fi.aalto.trafficsense.regularroutes.backend.rest.types;

import com.google.gson.annotations.SerializedName;

public class DeviceResponse {
    @SerializedName("device_id")
    public final String mDeviceId;

    @SerializedName(("device_token"))
    public final String mDeviceToken;

    public DeviceResponse(String mDeviceId, String  mDeviceToken) {
        this.mDeviceId = mDeviceId;
        this.mDeviceToken = mDeviceToken;
    }
}