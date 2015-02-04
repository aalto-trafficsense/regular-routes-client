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

import fi.aalto.trafficsense.regularroutes.util.ThreadGlue;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import timber.log.Timber;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RestClient {
    private static final String THREAD_NAME_FORMAT = "rest-client";

    /* Private Members */
    private final BackendStorage mStorage;
    private final ExecutorService mHttpExecutor;
    private final RestApi mApi;
    private final AtomicReference<Boolean> mUploadEnabled = new AtomicReference<>(true);
    private final AtomicReference<Boolean> mUploading = new AtomicReference<>(false);
    private final Object uploadingStateLock = new Object();
    private final ThreadGlue mThreadGlue = new ThreadGlue();

    private Optional<String> mSessionId = Optional.absent();

    /* Constructor(s) */
    public RestClient(Uri server, BackendStorage storage, Handler mainHandler) {
        this.mStorage = storage;
        this.mHttpExecutor = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat(THREAD_NAME_FORMAT).build());
        this.mApi = new RestAdapter.Builder()
                .setExecutors(mHttpExecutor, new HandlerExecutor(mainHandler))
                .setEndpoint(server.toString())
                .build().create(RestApi.class);
    }

    /* Public Methods */

    public boolean isUploadEnabled() {
        return mUploadEnabled.get();
    }

    public void setUploadEnabledState(boolean enabled) {
        mUploadEnabled.set(enabled);
    }

    /**
     * Wait until previous upload operation(s) are completed and then triggers data upload.
     * If uploading is disabled, method returns instantly.
     * @return true, if upload was triggered; false if upload is/was disabled
     **/
    public boolean  waitAndUploadData(final  DataQueue queue) throws InterruptedException{
        if (!isUploadEnabled())
            return false;

        boolean uploadTriggered = false;

        while (!uploadTriggered && isUploadEnabled()) {
            waitTillNotUploading();
            uploadTriggered = uploadData(queue);
        }

        return uploadTriggered;
    }

    /**
     * Triggers uploading if other upload process is not ongoing
     * @return false if upload is disabled, other uploading is ongoing and operation was therefore aborted; true otherwise.
     **/
    public boolean uploadData(final DataQueue queue) {
        mThreadGlue.verify();
        if (!isUploadEnabled() || isUploading())
            return false;

        if (queue.isEmpty()) {
            Timber.d("skipping upload operation: Queue is empty");
            return true;
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

        return true;
    }

    public void destroy() {
        mHttpExecutor.shutdownNow();
    }


    public boolean isUploading() {
        return mUploading.get();
    }

    /**
     * Waits till uploading is completed or disabled
     **/
    public void waitTillNotUploading() throws InterruptedException{
        synchronized (uploadingStateLock) {

            while(isUploadEnabled() && isUploading()) {
                uploadingStateLock.wait();
            }

        }
    }

    public void fetchDeviceId(final Callback<Integer> callback){
        authenticate(new Callback<Void>() {
            @Override
            public void run(Void result, RuntimeException error) {
                if (error != null) {
                    callback.run(null, error);
                    return;
                }

                // get device id
                devices(new Callback<Dictionary<String, Integer>>() {
                    @Override
                    public void run(Dictionary<String, Integer> result, RuntimeException error) {
                        if (error != null) {
                            callback.run(null, error);
                            return;
                        }
                        Optional<String> token = mStorage.readDeviceToken();
                        if (!token.isPresent()) {
                            callback.run(null, new RuntimeException("Couldn't resolve device token (uuid)"));
                            return;
                        }

                        /**
                         * Note: currently device token equals device uuid. This must be fixed, if
                         * that behaviour is modified
                         **/
                        final String deviceToken = mStorage.readDeviceToken().get();
                        Enumeration<String> iter = result.keys();
                        while(iter.hasMoreElements()) {
                            String deviceUuid = iter.nextElement();
                            if (deviceUuid.equals(deviceToken))
                            {
                                Integer deviceId = result.get(deviceUuid);
                                callback.run(deviceId, null);
                                return;
                            }
                        }
                        callback.run(null, null);

                    }
                });
            }
        });
    }

    /* Private Methods */
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

    /**
     * Trigger fetching device uuid-id dictionary from the server
     **/
    private void devices(final Callback<Dictionary<String, Integer>> callback) {
        mApi.devices(new retrofit.Callback<Response>() {

            @Override
            public void success(Response response, Response response2) {
                //Try to get response body
                BufferedReader reader = null;
                Dictionary<String, Integer> devices = new Hashtable<>();

                /**
                 * Response is list of lines in format <uuid> = <id>
                 **/
                String regex = "([^\\s=]+)(\\s*=\\s*)(.+)$";
                Pattern pattern = Pattern.compile(regex);
                try {

                    reader = new BufferedReader(new InputStreamReader(response.getBody().in()));

                    String line;

                    try {
                        while ((line = reader.readLine()) != null) {
                            Matcher m = pattern.matcher(line);
                            while(m.find()) {
                                String device_uuid = m.group(1);
                                String device_id = m.group(3);

                                devices.put(device_uuid, Integer.parseInt(device_id));
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }


                callback.run(devices, null);
            }

            @Override
            public void failure(RetrofitError error) {
                callback.run(null, new RuntimeException("Fetching devices failed", error));
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

    private void setUploading(boolean isUploading) {
        synchronized (uploadingStateLock) {
            mUploading.set(isUploading);
            if (!isUploading)
                uploadingStateLock.notifyAll();

        }
    }
}
