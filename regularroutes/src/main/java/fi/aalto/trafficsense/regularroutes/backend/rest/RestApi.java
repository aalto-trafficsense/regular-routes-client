package fi.aalto.trafficsense.regularroutes.backend.rest;

import org.json.JSONObject;

import fi.aalto.trafficsense.regularroutes.backend.rest.types.AuthenticateRequest;
import fi.aalto.trafficsense.regularroutes.backend.rest.types.AuthenticateResponse;
import fi.aalto.trafficsense.regularroutes.backend.rest.types.DataBody;
import fi.aalto.trafficsense.regularroutes.backend.rest.types.DeviceResponse;
import fi.aalto.trafficsense.regularroutes.backend.rest.types.RegisterRequest;
import fi.aalto.trafficsense.regularroutes.backend.rest.types.RegisterResponse;
import retrofit.Callback;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Path;
import retrofit.http.Query;

public interface RestApi {
    @POST("/register")
    void register(@Body RegisterRequest request,  Callback<RegisterResponse> callback);

    @POST("/authenticate")
    void authenticate(@Body AuthenticateRequest request, Callback<AuthenticateResponse> callback);

    @POST("/data")
    void data(@Query("sessionToken") String sessionToken, @Body DataBody body, Callback<JSONObject> callback);

    @GET("/device/{sessionToken}")
    void device(@Path("sessionToken") String sessionToken, Callback<DeviceResponse> callback);
}
