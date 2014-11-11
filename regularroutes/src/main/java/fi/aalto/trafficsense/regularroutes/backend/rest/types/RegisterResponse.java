package fi.aalto.trafficsense.regularroutes.backend.rest.types;

import com.google.gson.annotations.SerializedName;

public class RegisterResponse {
    @SerializedName("deviceToken")
    public final String mDeviceToken;

    public RegisterResponse(String deviceToken) {
        this.mDeviceToken = deviceToken;
    }
}
