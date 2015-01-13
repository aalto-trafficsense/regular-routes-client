package fi.aalto.trafficsense.funfprobes.FusedLocation;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.location.Location;

import com.google.android.gms.location.LocationClient;
import com.google.gson.Gson;

import timber.log.Timber;

public class FusedLocationService extends IntentService {

    public FusedLocationService() {
        super("FusedLocationService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Location location = intent.getParcelableExtra(LocationClient.KEY_LOCATION_CHANGED);
        if(location !=null){
            Timber.d("onHandleIntent " + location.getLatitude() + "," + location.getLongitude());
            handleLocationResult(location);
        }
    }

    private void handleLocationResult(final Location result) {
        Bundle data = new Bundle();
        // Get the most probable activity

        Gson gson = new Gson();
        String locationData = gson.toJson(result);
        data.putString("Location", locationData);
        data.putString("timestamp", String.valueOf(result.getTime()));
        Intent intent = new Intent(FusedLocationProbe.INTENT_ACTION);
        intent.putExtras(data);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }
}
