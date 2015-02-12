package fi.aalto.trafficsense.regularroutes.backend.rest;

import org.json.JSONObject;

import fi.aalto.trafficsense.regularroutes.backend.rest.types.AuthenticateResponse;
import fi.aalto.trafficsense.regularroutes.backend.rest.types.DataBody;
import fi.aalto.trafficsense.regularroutes.backend.rest.types.RegisterResponse;
import retrofit.Callback;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Query;

public interface RestApi {
    @POST("/register")
    void register(Callback<RegisterResponse> callback);

    @FormUrlEncoded
    @POST("/authenticate")
    void authenticate(@Field("deviceToken") String deviceToken, Callback<AuthenticateResponse> callback);

    @POST("/data")
    void data(@Query("sessionId") String sessionId, @Body DataBody body, Callback<JSONObject> callback);

    @GET("/devices")
    void devices(Callback<Response> callback);
}
