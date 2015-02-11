package fi.aalto.trafficsense.funfprobes.activityrecognition;


import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import edu.mit.media.funf.time.DecimalTimeUnit;

public class ActivityRecognitionIntentService extends IntentService {
    public ActivityRecognitionIntentService() {
        super("ActivityRecognitionService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            handleActivityRecognitionResult(ActivityRecognitionResult.extractResult(intent));
        }
    }

    /**
     * Broadcasts activity intent's important information with timestamp locally
     **/
    private void handleActivityRecognitionResult(final ActivityRecognitionResult result) {
        final int numberOfActivitiesCollected = 3;
        final Date now = new Date();
        final List<DetectedActivity> activities = result.getProbableActivities();

        // Collect enough data to construct the result on the receiver side
        Bundle bundle = new Bundle();

        HashMap<Integer, Integer> activityConfidenceMap = new HashMap<Integer, Integer>();
        for (DetectedActivity act : activities)
            activityConfidenceMap.put(act.getType(), act.getConfidence());

        bundle.putSerializable(ActivityRecognitionProbe.KEY_ACTIVITY_CONF_MAP, activityConfidenceMap);
        bundle.putString(ActivityRecognitionProbe.TIMESTAMP,
                DecimalTimeUnit.MILLISECONDS.toSeconds(BigDecimal.valueOf(now.getTime())).toString());

        Intent intent = new Intent(ActivityRecognitionProbe.INTENT_ACTION);
        intent.putExtras(bundle);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }
}
