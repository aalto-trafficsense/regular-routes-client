/**
 *
 * TODO: add MIT license text here
 *
 */
package fi.aalto.trafficsense.funfprobes.activityrecognition;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import com.google.gson.IJsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.probe.Probe;
import edu.mit.media.funf.probe.Probe.RequiredProbes;
import timber.log.Timber;


/**
 * The purpose of this probe is to detect when person is moving (by analyzing data from ActivityRecognition)
 * and if so send activity to all listeners. Other probes (such as FusedLocationProbe, AccelerometerSensor, and OrientationSensor),
 * that are both data and battery intensive can then use it as their "schedule" for sensing data
 *
 * @author Kimmo Karhu
 */

/* Funf configuration example:
{
    "@type": "edu.mit.media.funf.probe.builtin.AccelerometerSensorProbe",
    "sensorDelay": "NORMAL",
    "@schedule": {
                    "duration": 10,
                    "@type": "fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityFilterProbe",
                 }
}
 */

// TODO: Consider integrating with ActivityRecognition.

@RequiredProbes(ActivityRecognitionProbe.class)
//@Schedule.DefaultSchedule(interval=60)

public class ActivityFilterProbe extends Probe.Base implements Probe.ContinuousProbe {

    @Configurable
    private String startRegExp = "IN_VEHICLE|ON_BICYCLE|ON_FOOT|RUNNING|TILTING|UNKNOWN|WALKING";
    @Configurable
    private int startThreshold = 2;
    @Configurable
    private String stopRegExp = "STILL";
    @Configurable
    private int stopThreshold = 8;

    private int consecutiveCount=0;
    private Pattern startPattern;
    private Pattern stopPattern;
    private ActivityRecognitionListener listener;

    private class ActivityRecognitionListener implements DataListener {

		@Override
		public void onDataReceived(IJsonObject completeProbeUri, IJsonObject activityRecognitionData) {

            String activity=null;
            Matcher m;
            JsonElement j = activityRecognitionData.get("activities");
            if(j!=null)
            {
                if(j.isJsonArray())
                {
                    JsonArray ja = j.getAsJsonArray();
                    int confidence = 0;
                    for(JsonElement je : ja){
                        JsonObject jo = je.getAsJsonObject();
                        int newConfidence = jo.get("confidence").getAsInt();
                        if(newConfidence > confidence) {
                            activity = jo.get("activityType").getAsString();
                            confidence = newConfidence;
                        }
                    }
                    Timber.d("Activity with highest confidence: " + activity + ":" + confidence + "%");
                }
            }
            // return if activity cannot be parsed
            if(activity == null) {
                Timber.w("ActivityRecognitionListener:onDataReceived activity type parsing failed");
                return;
            }

            switch (getState()) {
                case RUNNING:
                    // if enough consecutive matches of "stop" activities then stop otherwise emit activity to other probes using this probe as scheduler
                    m = stopPattern.matcher(activity);
                    if(m.matches())
                        consecutiveCount++;
                    else
                        consecutiveCount = 0;
                    if(consecutiveCount >= stopThreshold) {
                        Timber.i("Going to sleep...");
                        // TODO: Figure out how to import InternalBroadcasts here
                        notifyProbeResults("GOING_TO_SLEEP");
                        consecutiveCount = 0;
                        stop();
                    }
                    else {
                        Timber.i("Staying awake...");
                        sendData(activityRecognitionData.getAsJsonObject());
                    }
                    break;
                case ENABLED:
                    // if enough consecutive matches of "start" activities then start and emit activity to other probes using this probe as scheduler, otherwise do nothing
                    m = startPattern.matcher(activity);
                    if(m.matches())
                        consecutiveCount++;
                    else
                        consecutiveCount = 0;
                    if(consecutiveCount >= startThreshold) {
                        consecutiveCount = 0;
                        Timber.i("Waking up...");
                        start();
                        notifyProbeResults("WAKING_UP");
                        sendData(activityRecognitionData.getAsJsonObject());
                    }
                    break;
            }
		}

		@Override
		public void onDataCompleted(IJsonObject completeProbeUri, JsonElement checkpoint) {
			// TODO Auto-generated method stub
		}
    	
    }

    @Override
    protected void onStart() {
        super.onStart();
        Timber.i("Activity Recognition Filter Probe started");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Timber.i("Activity Recognition Filter Probe stopped");
    }



    @Override
	protected void onEnable() {
        super.onEnable();
        startPattern = Pattern.compile(startRegExp);
        stopPattern = Pattern.compile(stopRegExp);
	    listener = new ActivityRecognitionListener();
        getGson().fromJson("{\"@type\":\"fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityRecognitionProbe\"}", ActivityRecognitionProbe.class).registerPassiveListener(listener);
	}

	@Override
	protected void onDisable() {
		super.onDisable();
        getGson().fromJson("{\"@type\":\"fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityRecognitionProbe\"}", ActivityRecognitionProbe.class).unregisterPassiveListener(listener);
    }

    private void notifyProbeResults(String messageType) {
        notifyProbeResults(messageType, null);
    }

    private void notifyProbeResults(String messageType, Bundle args) {
        LocalBroadcastManager mLocalBroadcastManager = LocalBroadcastManager.getInstance(getContext());
        if (mLocalBroadcastManager != null)
        {
            Intent intent = new Intent(messageType);
            if (args != null) {
                intent.putExtras(args);
            }

            mLocalBroadcastManager.sendBroadcast(intent);
            Timber.i("ActivityFilterProbe: Sending "+messageType);
        }
    }


}
