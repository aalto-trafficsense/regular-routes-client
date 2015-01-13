package fi.aalto.trafficsense.funfprobes.FusedLocation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONObject;
import java.util.Set;
import timber.log.Timber;


public class LocationBroadcastReceiver extends BroadcastReceiver{
    private FusedLocationProbe mProbe = null;

    public LocationBroadcastReceiver(FusedLocationProbe probe) {
        mProbe = probe;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if((mProbe != null) && !TextUtils.isEmpty(action)) {
            try {
                if (action.equals(FusedLocationProbe.INTENT_ACTION)) {
                    Timber.i("Fused Location Probe data received...");
                    Bundle bundle = intent.getExtras();
                    JSONObject json = null;
                    if(bundle != null) {
                        json = new JSONObject();
                        Set<String> keys = bundle.keySet();
                        for(String k : keys) {
                            try {
                                json.put(k, bundle.getString(k));
                            } catch (Exception e) {
                                Timber.e("Fused Location Probe data error: ", e);
                            }
                        }
                    }
                    if(json != null) {
                        JsonParser parser = new JsonParser();
                        JsonObject data = (JsonObject) parser.parse(json.toString());
                        mProbe.sendData(data);
                        Timber.i("Fused Location Probe data:");
                        Timber.i(json.toString());
                    }
                }
            } catch (Exception e) {
                Timber.e("Fused Location Probe data handing error: ", e);
            }
        }
    }
}
