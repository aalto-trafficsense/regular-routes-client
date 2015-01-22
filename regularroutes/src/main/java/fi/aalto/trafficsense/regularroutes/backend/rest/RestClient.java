package fi.aalto.trafficsense.regularroutes.backend.rest;

import android.net.Uri;
import android.os.Handler;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.backend.pipeline.DataQueue;
import fi.aalto.trafficsense.regularroutes.backend.rest.types.AuthenticateResponse;
import fi.aalto.trafficsense.regularroutes.backend.rest.types.DataBody;
import fi.aalto.trafficsense.regularroutes.backend.rest.types.RegisterResponse;
import fi.aalto.trafficsense.regularroutes.util.Callback;
import fi.aalto.trafficsense.regularroutes.util.HandlerExecutor;
import org.json.JSONObject;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import timber.log.Timber;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class RestClient {
    private static final String THREAD_NAME_FORMAT = "rest-client";

    private final BackendStorage mStorage;
    private final ExecutorService mHttpExecutor;
    private final RestApi mApi;

    private final AtomicReference<Boolean> mUploading = new AtomicReference<>(false);
    private Optional<String> mSessionId = Optional.absent();

    public RestClient(Uri server, BackendStorage storage, Handler mainHandler) {
        this.mStorage = storage;
        this.mHttpExecutor = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat(THREAD_NAME_FORMAT).build());
        this.mApi = new RestAdapter.Builder()
                .setExecutors(mHttpExecutor, new HandlerExecutor(mainHandler))
                .setEndpoint(server.toString())
                .build().create(RestApi.class);
    }

    private void register(final Callback<Void> callback) {
        mApi.register(new retrofit.Callback<RegisterResponse>() {

            @Override
            public void success(RegisterResponse registerResponse, Response response) {
                Timber.i("Registration succeeded");
                mStorage.writeDeviceToken(registerResponse.mDeviceToken);
                callback.run(null, null);
            }

            @Override
            public void failure(RetrofitError error) {
                callback.run(null, new RuntimeException("Registration failed", error));
            }
        });
    }

    private void authenticate(final Callback<Void> callback) {
        Optional<String> deviceToken = mStorage.readDeviceToken();
        if (!deviceToken.isPresent()) {
            register(new Callback<Void>() {
                @Override
                public void run(Void result, RuntimeException error) {
                    if (error != null) {
                        callback.run(null, error);
                        return;
                    }
                    authenticate(callback);
                }
            });
        } else {
            mApi.authenticate(deviceToken.get(), new retrofit.Callback<AuthenticateResponse>() {
                @Override
                public void success(AuthenticateResponse authenticateResponse, Response response) {
                    Timber.i("Authentication succeeded");
                    mSessionId = Optional.fromNullable(authenticateResponse.mSessionId);
                    callback.run(null, null);
                }

                @Override
                public void failure(RetrofitError error) {
                    if (error.getResponse().getStatus() == 403) {
                        mStorage.clearDeviceToken();
                    }
                    callback.run(null, new RuntimeException("Authentication failed", error));
                }
            });
        }
    }

    public void uploadData(final DataQueue queue) {
        if (isUploading())
            return;
        if (queue.isEmpty()) {
            Timber.d("skipping upload operation: Queue is empty");
            return;
        }

        setUploading(true);
        final DataBody body = DataBody.createSnapshot(queue);


        uploadDataInternal(body, new Callback<Void>() {
            @Override
            public void run(Void result, RuntimeException error) {
                setUploading(false);
                if (error != null) {
                    Timber.e(error, "Data upload failed");
                } else {
                    queue.removeUntilSequence(body.mSequence);
                    Timber.d("Uploaded data up to sequence #%d", body.mSequence);
                }
            }
        });
    }

    private void uploadDataInternal(final DataBody body, final Callback<Void> callback) {
        if (!mSessionId.isPresent()) {
            authenticate(new Callback<Void>() {
                @Override
                public void run(Void result, RuntimeException error) {
                    if (error != null) {
                        callback.run(null, error);
                        return;
                    }
                    uploadDataInternal(body, callback);
                }
            });
        } else {
            Timber.d("Uploading data...");
            mApi.data(mSessionId.get(), body, new retrofit.Callback<JSONObject>() {
                @Override
                public void success(JSONObject s, Response response) {
                    Timber.d("Data upload succeeded");
                    callback.run(null, null);
                }

                @Override
                public void failure(RetrofitError error) {
                    Timber.w("Data upload FAILED");
                    callback.run(null, new RuntimeException("Data upload failed", error));
                }
            });
        }
    }

    public void destroy() {
        mHttpExecutor.shutdownNow();
    }

    private void setUploading(boolean newValue) {
        mUploading.set(newValue);
    }

    public boolean isUploading() {
        return mUploading.get();
    }
}
