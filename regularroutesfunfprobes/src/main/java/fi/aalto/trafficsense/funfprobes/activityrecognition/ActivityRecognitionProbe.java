package fi.aalto.trafficsense.funfprobes.activityrecognition;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

import edu.mit.media.funf.Schedule;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.probe.Probe;
import edu.mit.media.funf.probe.Probe.Description;
import edu.mit.media.funf.probe.Probe.DisplayName;
import edu.mit.media.funf.probe.Probe.RequiredPermissions;

import edu.mit.media.funf.time.DecimalTimeUnit;
import timber.log.Timber;

@DisplayName("Trafficsense Google Activity probe")
@Description("Record activity data probided by Google Play Services")
@RequiredPermissions("com.google.android.gms.permission.ACTIVITY_RECOGNITION")
@Schedule.DefaultSchedule(interval=60)
public class ActivityRecognitionProbe
        extends
            Probe.Base
        implements
            Probe.ContinuousProbe,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

    /* Static Values */
    public final static String INTENT_ACTION =
            "fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityRecognitionProbe";

    private static DetectedProbeActivity latestDetectedActivity = new DetectedProbeActivity(DetectedActivity.UNKNOWN, 0);
    private static final Object latestDetectedActivityLock = new Object();

    // Configurations //
    @Configurable
    private int interval = 60; // unit, seconds


    /* Private Members */
    private final int REQUEST_CODE = 0;
    private GoogleApiClient mGoogleApiClient;
    private PendingIntent mCallbackIntent;
    private Gson mSerializerGson;
    private final ActivityRecognitionBroadcastReceiver mActivityRecognitionBroadcastReceiver;



    /* Constructor(s) */
    public ActivityRecognitionProbe() {

        mActivityRecognitionBroadcastReceiver = new ActivityRecognitionBroadcastReceiver();

    }

    /* Overriden Methods */
    @Override
    public void registerListener(DataListener... listeners) {
        Timber.d("ActivityRecognitionProbe: registerListener called");
        super.registerListener(listeners);
    }
    @Override
    public void unregisterListener(DataListener... listeners) {
        Timber.d("ActivityRecognitionProbe: unregisterListener called");
        super.unregisterListener(listeners);
    }

    @Override
    protected void onEnable() {
        super.onEnable();

        Timber.d("Activity Recognition Probe enabled");
        IntentFilter filter = new IntentFilter();
        filter.addAction(INTENT_ACTION);
        getContext().registerReceiver(mActivityRecognitionBroadcastReceiver, filter);
        mSerializerGson = getGsonBuilder().addSerializationExclusionStrategy(new ActivityExclusionStrategy()).create();
        registerApiClient();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        Timber.d("Activity Recognition Probe disabled");
        getContext().unregisterReceiver(mActivityRecognitionBroadcastReceiver);
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Timber.i("Activity Recognition Probe started");
        /*
        * This is continuous probe -> the location is received from enable to disable -period
        **/
    }

    @Override
    protected void onStop() {
        super.onStop();
        Timber.i("Activity Recognition Probe stopped");
        /*
        * This is continuous probe -> the location is received from enable to disable -period
        **/
    }

    @Override
    public void destroy() {
        super.destroy();
        Timber.i("Activity Recognition Probe destroyed");
    }


    @Override
    public void onConnectionSuspended(int i) {
        Timber.i("Activity Recognition Probe  connection suspend");
        unregisterApiClient();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Timber.w("Activity Recognition Probe  connection failed: " + result.toString());
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Timber.i("Activity Recognition Probe connected");

        // Activity Recognition receiver can be registered after api client is connected
        initActivityRecognitionClient();
    }

    /* Helper Methods */
    public void initGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this.getContext())
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    public void initActivityRecognitionClient() {
        if (mGoogleApiClient == null)
            return;

        Intent intent = new Intent(getContext().getApplicationContext(),
                fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityRecognitionProbe.ActivityRecognitionBroadcastReceiver.class);
        intent.setAction(INTENT_ACTION);

        mCallbackIntent = PendingIntent.getBroadcast(getContext(), REQUEST_CODE,
                intent, PendingIntent.FLAG_CANCEL_CURRENT);

        // subscribe for activity recognition updates
        final long intervalInMilliseconds = interval * 1000L;
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient,
                intervalInMilliseconds, mCallbackIntent);
        Timber.i("Started to request activity recognition updates with interval: " + interval);
    }


    public void registerApiClient() {
        int resp = GooglePlayServicesUtil.isGooglePlayServicesAvailable(
                getContext().getApplicationContext());
        if (resp == ConnectionResult.SUCCESS) {
            // Connect api client
            initGoogleApiClient();
            mGoogleApiClient.connect();
            Timber.i("Api client connected for ActivityRecognition");
        } else {
            Timber.w("Google Play Services is not installed. Fused Location Probe cannot be started");
            final Handler handler = new Handler(getContext().getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getContext().getApplicationContext(),
                            "Please install Google Play Service.", Toast.LENGTH_SHORT).show();
                }
            }, 5L);
        }
    }

    public void unregisterApiClient() {
        if(mGoogleApiClient != null) {
            ActivityRecognition.ActivityRecognitionApi
                    .removeActivityUpdates(mGoogleApiClient, mCallbackIntent);

            if (mGoogleApiClient.isConnected() || mGoogleApiClient.isConnecting())
                mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
        }
    }

    /* Static Methods */
    public static DetectedProbeActivity getLatestDetectedActivity() {
        synchronized (latestDetectedActivityLock) {
            return latestDetectedActivity;
        }
    }

    public static void setLatestDetectedActivity(DetectedProbeActivity detectedActivity) {
        synchronized (latestDetectedActivityLock) {
            latestDetectedActivity = detectedActivity;
        }
    }

    /* Helper class: ActivityRecognitionBroadcastReceiver */
    public class ActivityRecognitionBroadcastReceiver extends BroadcastReceiver {

        public ActivityRecognitionBroadcastReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Timber.d("ActivityRecognitionBroadcastReceiver: onReceive");
            if (intent.getAction() == null)
                return;

            if (intent.getAction().equals(INTENT_ACTION)) {
                Timber.d("ActivityRecognitionProbe: onReceive");
                if (ActivityRecognitionResult.hasResult(intent)) {
                    ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
                    DetectedProbeActivity detectedActivity = new DetectedProbeActivity(result.getMostProbableActivity());
                    JsonObject data = mSerializerGson.toJsonTree(detectedActivity).getAsJsonObject();

                    final Date now = new Date();
                    data.addProperty(TIMESTAMP,
                            DecimalTimeUnit.MILLISECONDS.toSeconds(BigDecimal.valueOf(now.getTime())));

                    Timber.d("Activity recognition data received: " + detectedActivity.asString()
                            + "(confidence level: " + detectedActivity.getConfidence() + ")");
                    Timber.d(mSerializerGson.toJson(data));
                    setLatestDetectedActivity(detectedActivity);
                    sendData(data);
                }
            }
        }
    }

}
