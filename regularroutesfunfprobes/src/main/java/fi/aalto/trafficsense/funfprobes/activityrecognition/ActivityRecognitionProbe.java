package fi.aalto.trafficsense.funfprobes.activityrecognition;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import edu.mit.media.funf.Schedule;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.probe.Probe;
import edu.mit.media.funf.probe.Probe.Description;
import edu.mit.media.funf.probe.Probe.DisplayName;
import edu.mit.media.funf.probe.Probe.RequiredPermissions;

import edu.mit.media.funf.time.DecimalTimeUnit;
import timber.log.Timber;

@DisplayName("Trafficsense Google Activity probe")
@Description("Record activity data provided by Google Play Services")
@RequiredPermissions("com.google.android.gms.permission.ACTIVITY_RECOGNITION")
@Schedule.DefaultSchedule(interval=60)
public class ActivityRecognitionProbe
        extends
            Probe.Base
        implements
            Probe.ContinuousProbe,
            Probe.PassiveProbe,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

    /* Static Values */
    public final static String INTENT_ACTION =
            "fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityRecognitionProbe";

    public final static String KEY_ACTIVITY_CONF_MAP = "ACTIVITY_TYPE";

    private static AtomicReference<ActivityDataContainer> sLatestDetectedActivity =
            new AtomicReference<>(new ActivityDataContainer(DetectedActivity.UNKNOWN, 0));

    private static AtomicReference<JsonObject> latestData = new AtomicReference<>();

    // Configurations //
    @Configurable
    private int interval = 10; // unit, seconds


    /* Private Members */
    private final int REQUEST_CODE = 0;
    private GoogleApiClient mGoogleApiClient;
    private PendingIntent mCallbackIntent;
    private ActivityRecognitionBroadcastReceiver mBroadcastReceiver = null;

    /* Overriden Methods */


    @Override
    public void registerPassiveListener(DataListener... listeners) {
        super.registerPassiveListener(listeners);
    }

    @Override
    public void unregisterPassiveListener(DataListener... listeners) {
        super.unregisterPassiveListener(listeners);
    }

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
        Gson serializerGson = getGsonBuilder().addSerializationExclusionStrategy(new ActivityExclusionStrategy()).create();
        if (mBroadcastReceiver == null)
            mBroadcastReceiver = new ActivityRecognitionBroadcastReceiver(this, serializerGson);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mBroadcastReceiver, new IntentFilter(INTENT_ACTION));
        registerApiClient();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        Timber.d("Activity Recognition Probe disabled");
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mBroadcastReceiver);
        if (mGoogleApiClient != null)
            mGoogleApiClient.disconnect();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Timber.i("Activity Recognition Probe started");
        sendData(latestData.get());
    }

    @Override
    protected void onStop() {
        super.onStop();
        Timber.i("Activity Recognition Probe stopped");
        // This is continuous probe -> the location is received from enable to disable -period
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
                fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityRecognitionIntentService.class);

        /**
         * Note: FLAG_CANCEL_CURRENT should be used with this intent instead of FLAG_UPDATE_CURRENT
         * Otherwise this probe may stop working after updating the app with new APK
         * See https://code.google.com/p/android/issues/detail?id=61850 for more details
         **/
        mCallbackIntent = PendingIntent.getService(getContext(), REQUEST_CODE,
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
            if (mGoogleApiClient != null) {
                mGoogleApiClient.connect();
                Timber.i("Api client connected for ActivityRecognition");
            }

        } else {
            Timber.w("Google Play Services is not installed. Activity Recognition Probe cannot be started");
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
    public static ActivityDataContainer getLatestDetectedActivities() {
            return sLatestDetectedActivity.get();

    }

    public static void setLatestDetectedActivities(ActivityDataContainer detectedActivity) {
            sLatestDetectedActivity.set(detectedActivity);
    }

    /* Helper class: ActivityRecognitionBroadcastReceiver */
    public static class ActivityRecognitionBroadcastReceiver extends BroadcastReceiver {
        private final ActivityRecognitionProbe mProbe;
        private final Gson mSerializerGson;

        public ActivityRecognitionBroadcastReceiver(ActivityRecognitionProbe probe, Gson serializerGson) {
            super();
            mProbe = probe;
            mSerializerGson = serializerGson;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null)
                return;

            if (intent.getAction().equals(INTENT_ACTION)) {

                ActivityDataContainer detectedActivities = parseActivityFromBroadcast(intent);
                setLatestDetectedActivities(detectedActivities);
                JsonObject j = mSerializerGson.toJsonTree(detectedActivities).getAsJsonObject();
                j.addProperty(TIMESTAMP, getTimeStampFromBroadcast(intent));
                latestData.set(j);
                Timber.d(mSerializerGson.toJson(j));
            }
        }

        /* Private Helpers */
        private ActivityDataContainer parseActivityFromBroadcast(Intent intent) {
            if (intent == null)
                return null;

            Bundle bundle = intent.getExtras();
            if (bundle == null)
                return null;

            ActivityDataContainer container;

            if (bundle.containsKey(KEY_ACTIVITY_CONF_MAP)) {
                HashMap<Integer, Integer> activityConfidenceMap = (HashMap<Integer, Integer>)bundle.getSerializable(KEY_ACTIVITY_CONF_MAP);
                container = new ActivityDataContainer(activityConfidenceMap);
            }
            else
                container = new ActivityDataContainer();

            return container;
        }

        private BigDecimal getTimeStampFromBroadcast(Intent intent) {
            if (intent == null)
                return null;

            BigDecimal timeStamp;
            Bundle bundle = intent.getExtras();
            if (bundle == null || !bundle.containsKey(TIMESTAMP)) {
                final Date now = new Date();
                timeStamp = BigDecimal.valueOf(now.getTime());
            }
            else {
                timeStamp = new BigDecimal(bundle.getString(TIMESTAMP));
            }

            return DecimalTimeUnit.MILLISECONDS.toSeconds(timeStamp);
        }
    }

}
