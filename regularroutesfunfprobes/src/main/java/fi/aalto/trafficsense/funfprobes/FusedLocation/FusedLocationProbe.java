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
import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityRecognitionProbe;
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

    private StateListener stateListener;

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
        Timber.d("FusedLocationProbe: registerListener called");
        super.registerListener(listeners);
    }
    @Override
    public void unregisterListener(DataListener... listeners) {
        Timber.d("FusedLocationProbe: unregisterListener called");
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
        if(mGoogleApiClient == null)
            registerApiClient();
        else if(!mGoogleApiClient.isConnecting())
            initLocationClient();
        Timber.i("Fused Location Probe started");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mGoogleApiClient != null /*&& mListener != null*/)
            //LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, mListener);
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        Timber.i("Fused Location Probe stopped");
    }

    @Override
    public void destroy() {
        super.destroy();
        Timber.i("Fused Location Probe destroyed");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Timber.i("Fused Location Probe connection suspend");
        unregisterApiClient();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Timber.w("Fused Location Probe connection to Google Location API failed: " + result.toString());
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Timber.i("Fused Location Probe connected to Google Location API");
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
            //LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, mListener);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            Timber.i("Started to request location updates with interval: " + interval);
        }
        else
            mGoogleApiClient.connect();
    }


    public void registerApiClient() {
        int resp = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getContext().getApplicationContext());
        if (resp == ConnectionResult.SUCCESS) {
            // Connect to the LocationService
            initGoogleApiClient();
            if (mGoogleApiClient != null) {
                mGoogleApiClient.connect();
                Timber.i("Initiating location client connect...");
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

    /* Helper class: FusedLocationListener */
    //public class FusedLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            if (location != null) {
                JsonObject data = mSerializerGson.toJsonTree(location).getAsJsonObject();
                data.addProperty(TIMESTAMP, DecimalTimeUnit.MILLISECONDS.toSeconds(data.get("mTime").getAsBigDecimal()));
                Timber.d("Location data received");
                Timber.d(mSerializerGson.toJson(data));
                setLatestReceivedLocation(location);
                sendData(data);
                Timber.d("Location data sent");
            }
        }
    //}
}
