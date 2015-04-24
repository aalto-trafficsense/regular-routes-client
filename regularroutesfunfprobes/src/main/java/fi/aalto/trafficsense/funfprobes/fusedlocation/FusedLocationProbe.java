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

    // Configurations //
    @Configurable
    private int interval = 10; // unit, seconds

    @Configurable
    private int fastestInterval = 5000; // milliseconds

    @Configurable
    private int priority = LocationRequest.PRIORITY_HIGH_ACCURACY;

    @Override
    public void onStateChanged(Probe probe, State previousState) {
        if(probe instanceof ActivityFilterProbe)
        {
            // follow the states of ActivityFilterProbe
            if(getState()==State.RUNNING || probe.getState()==State.ENABLED)
                stop();
            else if(getState()==State.ENABLED || probe.getState()==State.RUNNING)
                start();
        }
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
        // start actively requesting locations
        requestLocationUpdates(priority);
        Timber.d("Fused Location Probe started");
    }

    @Override
    protected void onStop() {
        super.onStop();
        // stop actively requesting locations but listen them passively
        requestLocationUpdates(LocationRequest.PRIORITY_NO_POWER);
        Timber.d("Fused Location Probe stopped");
    }

    @Override
    public void destroy() {
        super.destroy();
    }

    /* Google API Client call backs */

    @Override
    public void onConnectionSuspended(int i) {
        Timber.d("Fused Location Probe connection suspend");
        unregisterApiClient();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Timber.w("Fused Location Probe connection to Google Location API failed: " + result.toString());
        // TODO https://developer.android.com/google/auth/api-client.html
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        // depending on the probe's state at the time of getting connected, request different priority
        if(getState()==State.ENABLED) {
            // if probe has just been enabled or stopped
            requestLocationUpdates(LocationRequest.PRIORITY_NO_POWER);
        }
        else if(getState()==State.RUNNING) {
            // if probe has just been started
            requestLocationUpdates(priority);
        }
        Timber.d("Fused Location Probe connected to Google Location API");
    }

    /* Helper Methods */

    /* This method contains re-usable logic to recover from different Google API client connection
    *  states, builds the LocationRequest object, and then uses it to request location updates
    * */
    public void requestLocationUpdates(int reqPriority) {

        if(mGoogleApiClient == null) {
            Timber.d("Google Location API Client was null, re-registering ...");
            registerApiClient();
        }
        else {
            if(!mGoogleApiClient.isConnecting()) {
                // do nothing and wait connection to realize
                Timber.d("Google API client is already connecting...");
            }
            else if (mGoogleApiClient.isConnected()) {
                // This is the normal case ...
                // Set location request settings
                LocationRequest mLocationRequest = LocationRequest.create();
                mLocationRequest.setInterval(interval);
                mLocationRequest.setFastestInterval(fastestInterval);
                mLocationRequest.setPriority(reqPriority);
                // subscribe for location updates
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
                Timber.i("Started to request location updates with interval: " + interval);
            }
            else {
                Timber.d("Google Location API Client is not connected!");
                // if not connected, connect (if we have lost connection, better try to pro-actively re-establish it before needed next time)
                mGoogleApiClient.connect();
            }
        }

    }

    public void registerApiClient() {
        int resp = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getContext().getApplicationContext());
        if (resp == ConnectionResult.SUCCESS) {
            // Connect to the LocationService
            mGoogleApiClient = new GoogleApiClient.Builder(this.getContext())
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
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
            sendData(data);
            Timber.d("Location data passed from Funf probe");
        }
    }
}
