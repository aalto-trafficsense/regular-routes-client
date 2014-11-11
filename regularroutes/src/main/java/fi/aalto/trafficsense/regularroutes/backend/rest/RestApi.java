package fi.aalto.trafficsense.regularroutes.backend.rest;

import fi.aalto.trafficsense.regularroutes.backend.rest.types.AuthenticateResponse;
import fi.aalto.trafficsense.regularroutes.backend.rest.types.DataBody;
import fi.aalto.trafficsense.regularroutes.backend.rest.types.RegisterResponse;
import org.json.JSONObject;
import retrofit.Callback;
import retrofit.http.*;

public interface RestApi {
    @POST("/register")
    void register(Callback<RegisterResponse> callback);

    @FormUrlEncoded
    @POST("/authenticate")
    void authenticate(@Field("deviceToken") String deviceToken, Callback<AuthenticateResponse> callback);

    @POST("/data")
    void data(@Query("sessionId") String sessionId, @Body DataBody body, Callback<JSONObject> callback);
}
