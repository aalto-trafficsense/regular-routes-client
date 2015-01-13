package fi.aalto.trafficsense.funfprobes.FusedLocation;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import edu.mit.media.funf.Schedule;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.probe.Probe;
import edu.mit.media.funf.probe.Probe.Description;
import edu.mit.media.funf.probe.Probe.DisplayName;
import edu.mit.media.funf.probe.Probe.RequiredPermissions;

import timber.log.Timber;

@DisplayName("Trafficsense fused location probe")
@Description("Record location data")
@RequiredPermissions("android.permission.ACCESS_FINE_LOCATION")
@Schedule.DefaultSchedule(interval=60000)
public class FusedLocationProbe
        extends Probe.Base
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    /* Static Values */
    public final static String INTENT_ACTION = "fi.aalto.trafficsense.funfprobes.FusedLocation.FusedLocationProbe";

    /* Private Members */
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private LocationBroadcastReceiver mLocationBroadcastReceiver;
    private final int requestCode = 0;
    PendingIntent mCallbackIntent;

    @Configurable
    private int interval = 60000; // unit, millisecond

    private int fastestInverval = 15000;
    private int priority = LocationRequest.PRIORITY_HIGH_ACCURACY;


    @Override
    protected void onEnable() {
        super.onEnable();

        initReceiver();
        registerLocationClient();
    }

    @Override
    protected void onDisable() {
        super.onDisable();

        unregisterReceiver();
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    public void onConnectionSuspended(int i) {
        Timber.i("Fused Location Probe  connection suspend");
        unregisterLocationClient();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Timber.w("Fused Location Probe  connection failed: " + result.toString());
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Timber.i("Fused Location Probe connected");
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

        Intent intent = new Intent(getContext().getApplicationContext(), FusedLocationService.class);
        mCallbackIntent = PendingIntent.getService(getContext(), requestCode,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(interval);
        mLocationRequest.setFastestInterval(fastestInverval);
        mLocationRequest.setPriority(priority);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, mCallbackIntent);
        Timber.i("Started to request location updates with interval: " + interval);
    }

    public void initReceiver() {
        if(null == mLocationBroadcastReceiver) {
            mLocationBroadcastReceiver = new LocationBroadcastReceiver(this);
        }
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mLocationBroadcastReceiver,
                new IntentFilter(INTENT_ACTION));

    }

    public void unregisterReceiver() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mLocationBroadcastReceiver);

    }

    public void registerLocationClient() {
        int resp = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getContext().getApplicationContext());
        if (resp == ConnectionResult.SUCCESS) {
            // Connect to the LocationService
            initGoogleApiClient();
            mGoogleApiClient.connect();
            Timber.i("Location client connected");
        } else {
            Timber.w("Google Play Services is not installed. Fused Location Probe cannot be started");
            final Handler handler = new Handler(getContext().getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getContext(),
                            "Please install Google Play Service.", Toast.LENGTH_SHORT).show();
                }
            }, 5L);
        }
    }

    public void unregisterLocationClient() {
        if(mGoogleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, mCallbackIntent);
            if (mGoogleApiClient.isConnected() || mGoogleApiClient.isConnecting())
                mGoogleApiClient.disconnect();
            mGoogleApiClient = null;
        }
    }

    public void sendData(com.google.gson.JsonObject data) {
        super.sendData(data);
    }
}
