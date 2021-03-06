package fi.aalto.trafficsense.funfprobes.fusedlocation;

import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.concurrent.atomic.AtomicReference;

import edu.mit.media.funf.Schedule;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.probe.Probe;
import edu.mit.media.funf.probe.Probe.Description;
import edu.mit.media.funf.probe.Probe.DisplayName;
import edu.mit.media.funf.probe.Probe.RequiredPermissions;

import edu.mit.media.funf.probe.builtin.ProbeKeys;
import edu.mit.media.funf.time.DecimalTimeUnit;
import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityFilterProbe;
import timber.log.Timber;

// TODO: Consider states onStart - onStop for global existence, onActive - onInactive for power saving

@DisplayName("Trafficsense fused location probe")
@Description("Record location data")
@RequiredPermissions({android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION})
@Schedule.DefaultSchedule(interval=60)
public class FusedLocationProbe
        extends
            Probe.Base
        implements
            Probe.ContinuousProbe,
            Probe.StateListener,
            ProbeKeys.LocationKeys,
            LocationListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener{

    /* Static Values */
    public final static String INTENT_ACTION = "fi.aalto.trafficsense.funfprobes.fusedlocation.FusedLocationProbe";
    private static AtomicReference<Location> sLatestReceivedLocation = new AtomicReference<>();

    /* Private Members */
    private GoogleApiClient mGoogleApiClient;
    //private FusedLocationListener mListener = new FusedLocationListener();
    private Gson mSerializerGson;

    static private FusedLocationProbe selfInstance;

    // Configurations //
    @Configurable
    private int interval = 10; // unit, seconds

    @Configurable
    private int fastestInterval = 5000; // milliseconds

    @Configurable
    private int priority = LocationRequest.PRIORITY_HIGH_ACCURACY;

    @Configurable
    private int sleep_priority = LocationRequest.PRIORITY_NO_POWER;

    @Override
    public void onStateChanged(Probe probe, State previousState) {
    /* MJR: Commenting this out 6.11.2015 - No reason whatsoever to connect to ActivityFilterProbe states
        if(probe instanceof ActivityFilterProbe)
        {
            // follow the states of ActivityFilterProbe
            if(getState()==State.RUNNING || probe.getState()==State.ENABLED)
                stop();
            else if(getState()==State.ENABLED || probe.getState()==State.RUNNING)
                start();
        }
    */
    }

    /* Overriden Methods */
    @Override
    public void registerListener(DataListener... listeners) {
        super.registerListener(listeners);
    }
    @Override
    public void unregisterListener(DataListener... listeners) {
        super.unregisterListener(listeners);
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        selfInstance = this;
        registerApiClient();
        mSerializerGson = getGsonBuilder().addSerializationExclusionStrategy(new FusedLocationExclusionStrategy()).create();
        getGson().fromJson("{\"@type\":\"fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityFilterProbe\"}", ActivityFilterProbe.class).addStateListener(this);
        Timber.d("Fused Location Probe enabled");
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        unregisterApiClient();
        getGson().fromJson("{\"@type\":\"fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityFilterProbe\"}", ActivityFilterProbe.class).removeStateListener(this);
        Timber.d("Fused Location Probe disabled");
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(mGoogleApiClient == null)
            registerApiClient();
        else if(!mGoogleApiClient.isConnecting())
            initLocationClient();
        Timber.d("Fused Location Probe started");
    }

    @Override
    protected void onStop() {
        super.onStop();

        if(mGoogleApiClient != null)
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        Timber.d("Fused Location Probe stopped");

    }

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Timber.d("Fused Location Probe connection suspend");
        unregisterApiClient();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Timber.w("Fused Location Probe connection to Google Location API failed: " + result.toString());
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Timber.d("Fused Location Probe connected to Google Location API");
        initLocationClient();
    }

    /* Helper Methods */
    public void initGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this.getContext())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    public void initLocationClient() {
        if (mGoogleApiClient == null)
            return;

        if(mGoogleApiClient.isConnected()) {
            // Set location request settings
            LocationRequest mLocationRequest = LocationRequest.create();
            mLocationRequest.setInterval(interval);
            mLocationRequest.setFastestInterval(fastestInterval);
            mLocationRequest.setPriority(priority);

            // subscribe for location updates
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            Timber.i("Started to request location updates with interval: " + interval);

        }
        else
            mGoogleApiClient.connect();
    }

    private void setLocationFrequency(boolean awake) {
        if (mGoogleApiClient == null)
            return;

        if(mGoogleApiClient.isConnected()) {
            // Set location request settings
            LocationRequest mLocationRequest = LocationRequest.create();
            mLocationRequest.setInterval(interval);
            mLocationRequest.setFastestInterval(fastestInterval);
            if (awake) {
                mLocationRequest.setPriority(priority);
                Timber.i("Location probe awake");
            } else {
                mLocationRequest.setPriority(sleep_priority);
                Timber.i("Location probe asleep");
            }

            // subscribe for location updates
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);

        }
        else
            mGoogleApiClient.connect();
    }

    // TODO: Figure out a better solution to the static - non-static mess
    static public void wakeUp() {
        if (selfInstance!=null) selfInstance.setLocationFrequency(true);
    }

    static public void goToSleep() {
        if (selfInstance!=null) selfInstance.setLocationFrequency(false);
    }

    public void registerApiClient() {
        int resp = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getContext().getApplicationContext());
        if (resp == ConnectionResult.SUCCESS) {
            // Connect to the LocationService
            initGoogleApiClient();
            if (mGoogleApiClient != null) {
                mGoogleApiClient.connect();
                Timber.d("Initiating location client connect...");
            }

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
            //LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, mListener);
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            if (mGoogleApiClient.isConnected() || mGoogleApiClient.isConnecting())
                mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
        }
    }

    /* Static Methods */
    public static Location getLatestReceivedLocation() {
            return sLatestReceivedLocation.get();

    }

    public static void setLatestReceivedLocation(Location receivedLocation) {
            sLatestReceivedLocation.set(receivedLocation);
    }

    /* changed the Probe itself to be LocationListener so that when sendData is called
       onLocationChanged() it will happen on probes own thread as expected by Probe.onSend()
    */
    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            JsonObject data = mSerializerGson.toJsonTree(location).getAsJsonObject();
            data.addProperty(TIMESTAMP, DecimalTimeUnit.MILLISECONDS.toSeconds(data.get("mTime").getAsBigDecimal()));
            setLatestReceivedLocation(location);
            Timber.d("Passing fused location data from probe");
            // TODO: When eradicating funf, this should be sending directly to DataCollector listener
            sendData(data);
            Timber.d("Location data passed from Funf probe");
        }
    }
}
