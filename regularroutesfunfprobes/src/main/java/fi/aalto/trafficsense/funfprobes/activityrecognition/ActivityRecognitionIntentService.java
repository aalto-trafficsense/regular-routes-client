package fi.aalto.trafficsense.funfprobes.activityrecognition;


import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.math.BigDecimal;
import java.util.Date;
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
        final Date now = new Date();
        DetectedActivity bestActivityCandidate = result.getMostProbableActivity();

        if (bestActivityCandidate.getType() == DetectedActivity.ON_FOOT) {
            // Try to detect the more accurate sub type (running or walking)
            List<DetectedActivity> activities = result.getProbableActivities();
            if (activities.size() > 1) {
                DetectedActivity candidate = activities.get(1); // get next probably activity
                if (candidate.getType() == DetectedActivity.RUNNING || candidate.getType() == DetectedActivity.WALKING)
                    bestActivityCandidate = candidate; // Use the more accurate activity

            }
        }

        // Collect enough data to construct the result on the receiver side
        Bundle bundle = new Bundle();
        bundle.putInt(ActivityRecognitionProbe.KEY_ACTIVITY_TYPE, bestActivityCandidate.getType());
        bundle.putInt(ActivityRecognitionProbe.KEY_ACTIVITY_CONFIDENCE, bestActivityCandidate.getConfidence());
        bundle.putString(ActivityRecognitionProbe.TIMESTAMP,
                DecimalTimeUnit.MILLISECONDS.toSeconds(BigDecimal.valueOf(now.getTime())).toString());

        Intent intent = new Intent(ActivityRecognitionProbe.INTENT_ACTION);
        intent.putExtras(bundle);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }
}
