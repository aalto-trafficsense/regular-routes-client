package fi.aalto.trafficsense.regularroutes.ui;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityDataContainer;
import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityRecognitionProbe;
import fi.aalto.trafficsense.funfprobes.activityrecognition.DetectedProbeActivity;
import fi.aalto.trafficsense.funfprobes.fusedlocation.FusedLocationProbe;
import fi.aalto.trafficsense.regularroutes.R;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.backend.RegularRoutesPipeline;
import fi.aalto.trafficsense.regularroutes.util.Callback;


public class ConfigFragment extends Fragment {
    /* Private Members */
    private Activity mActivity;
    private Handler mHandler = new Handler();
    private final int notificationId = 8001;
    private NotificationManager mNotificationManager;
    private ActivityDataContainer mLastDetectedProbeActivities = null;
    private Location mLastReceivedLocation = null;
    private String mLastServiceRunningState = null;

    private Runnable mServiceStatusChecker = new Runnable() {
        @Override
        public void run() {
            final int statusCheckInterval = 1000;
            updateStatusData();
            mHandler.postDelayed(mServiceStatusChecker, statusCheckInterval);
        }
    };

    /* Constructor(s) */
    public ConfigFragment() {super();}

    /* Event Handlers */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_config, container, false);
    }


    @Override
    public void onActivityCreated(Bundle bundle)
    {
        super.onActivityCreated(bundle);
        mActivity = getActivity();
        mNotificationManager = (NotificationManager) mActivity.getSystemService(Context.NOTIFICATION_SERVICE);
        initButtonHandlers();
        startServiceStatusChecking();
        updateTokenField();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        mActivity = getActivity();
    }

    @Override
    public void onPause()
    {
        super.onPause();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopServiceStatusChecking();
    }


    /* Private Helper Methods */

    private void updateUploadSwitchState() {
        // Init upload toggle state
        final Switch uploadSwitch = (Switch) mActivity.findViewById(R.id.config_UploadEnabledSwitch);
        final boolean enabledState = getUploadEnabledState();
        if (uploadSwitch != null) {

            if (enabledState != uploadSwitch.isChecked())
                uploadSwitch.setChecked(getUploadEnabledState());
        }

    }

    private void initButtonHandlers() {
        if (mActivity != null) {
            final Button startButton = (Button) mActivity.findViewById(R.id.config_StartService);
            final Button stopButton = (Button) mActivity.findViewById(R.id.config_StopService);
            final Switch uploadSwitch = (Switch) mActivity.findViewById(R.id.config_UploadEnabledSwitch);
            if (startButton != null) {
                startButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startService();
                    }
                });
            }

            if (stopButton != null) {
                stopButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        stopService();
                    }
                });
            }

            // Visualize-button
            final Button visualizeButton =  (Button) mActivity.findViewById(R.id.config_VisualizeButton);
            if (visualizeButton != null) {
                visualizeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        visualize();
                    }
                });
            }

            // Upload enabled switch
            if (uploadSwitch != null) {
                uploadSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        setUploadEnabledState(isChecked);
                    }
                });
            }
        }
    }

    private boolean setUploadEnabledState(boolean enabled) {
        return RegularRoutesPipeline.setUploadEnabledState(enabled);
    }

    private boolean getUploadEnabledState() {
       return RegularRoutesPipeline.isUploadEnabled();
    }

    private boolean getUploadingState() {
        return RegularRoutesPipeline.isUploading();
    }

    private boolean setServiceRunning(boolean isRunning) {
        MainActivity mainActivity = (MainActivity) mActivity;
        if (mainActivity != null) {
            mainActivity.getBackendService().setRunning(isRunning);
            return true;
        }
        return false;
    }

    public String getServiceRunningState() {
        final String defaultValue = "Unknown";

        if (mActivity != null) {
            MainActivity mainActivity = (MainActivity) mActivity;
            if (null != mainActivity) {

                if (mainActivity.getBackendService() != null) {
                    Boolean isRunning = mainActivity.getBackendService().isRunning();
                    if (isRunning)
                        return "Running";
                    else
                        return "Not running";
                }
            }
        }

        return defaultValue;
    }

    public boolean startService() {
        boolean started = false;
        setButtonStates(false);
        if (isServiceRunning()) {
            showToast("Background service already running");
        }
        else if (setServiceRunning(true)) {
            started = true;
            showToast("Background service starting");
        }
        else {
            showToast("Background service starting failed");
        }
        setButtonStates(true);
        return started;
    }

    public boolean stopService() {
        boolean stopped = false;
        setButtonStates(false);
        if (!isServiceRunning()) {
            showToast("Background service already stopped");

        }
        else if (setServiceRunning(false)) {
            stopped = true;
            showToast("Background service stopping");
        }
        else {
            showToast("Background service stopping failed");
        }
        setButtonStates(true);
        return stopped;
    }

    /**
     * Call server's visualization service through web browser
     **/
    private void visualize() {
        if (mActivity == null) {
            showToast("Cannot visualize: Activity is null");
            return;
        }

        RegularRoutesPipeline.fetchDeviceId(new Callback<Integer>() {
            @Override
            public void run(Integer deviceId, RuntimeException error) {
                if (error != null || deviceId == null) {
                    showToast("Cannot visualize: Failed to get device id");
                    return;
                }
                else {
                    updateTokenField(); // token should be fetched now if it was not prev.available
                    showToast("Starting visualization...");
                    Uri baseUri = RegularRoutesPipeline.getConfig().server;
                    Uri serviceUri = Uri.withAppendedPath(baseUri, "visualize/" + deviceId);
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, serviceUri);
                    mActivity.startActivity(browserIntent);
                }
            }
        });
    }

    public boolean isServiceRunning() {

        if (mActivity != null) {
            MainActivity mainActivity = (MainActivity) mActivity;
            if (null != mainActivity) {

                if (mainActivity.getBackendService() != null) {
                    return mainActivity.getBackendService().isRunning();

                }
            }
        }

        return false;
    }


    private void updateStatusData() {
        final String serviceRunningState = getServiceRunningState();
        final ActivityDataContainer detectedActivities = ActivityRecognitionProbe.getLatestDetectedActivities();
        final Location receivedLocation = FusedLocationProbe.getLatestReceivedLocation();

        if (!(isNewServiceRunningState(serviceRunningState) || isNewActivityRecognitionProbeValue(detectedActivities) || isNewLocationProbeValue(receivedLocation)) )
            return; // nothing has been updated

        updateApplicationFields(serviceRunningState, detectedActivities, receivedLocation);
        updateNotification(serviceRunningState, detectedActivities, receivedLocation);

    }

    private void updateApplicationFields(final String serviceRunningState,
                                         final ActivityDataContainer detectedActivities,
                                         final Location receivedLocation) {

        if (mActivity == null)
            return;

        final TextView txtStatus = (TextView)mActivity.findViewById(R.id.config_serviceStatus);


        if (txtStatus != null) {
            txtStatus.setText(serviceRunningState);
        }

        if (detectedActivities != null) {
            final DetectedProbeActivity detectedProbeActivity = detectedActivities.getFirst();
            final TextView txtActivity = (TextView)mActivity.findViewById(R.id.config_activity);
            if (txtActivity != null) {
                String txt = String.format("%s (confidence: %s, %d%%)",
                        detectedProbeActivity.asString(),
                        detectedProbeActivity.getConfidenceLevelAsString(),
                        detectedProbeActivity.getConfidence());

                txtActivity.setText(txt);
            }


        }

        if (receivedLocation != null) {
            final TextView txtLocation = (TextView)mActivity.findViewById(R.id.config_location);
            final TextView txtLocation_row2 = (TextView)mActivity.findViewById(R.id.config_location_row2);
            if (txtLocation != null) {
                String txt = String.format("(%f, %f)", receivedLocation.getLongitude(), receivedLocation.getLatitude());
                txtLocation.setText(txt);
            }
            if (txtLocation_row2 != null) {
                String txt = String.format("Accuracy: %.0fm", receivedLocation.getAccuracy());
                txtLocation_row2.setText(txt);
            }
        }

        updateUploadSwitchState();
    }

    private void updateNotification(final String serviceRunningState,
                                    final ActivityDataContainer detectedActivities,
                                    final Location receivedLocation) {
        final String title = "Regular Routes Client";
        final String serviceStateText = getString(R.string.config_serviceRunningLabel) + serviceRunningState;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getActivity())
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(serviceStateText)
                .setOngoing(true);

        if (detectedActivities != null || receivedLocation != null) {
            // Use big style (multiple rows)
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle(title);
            inboxStyle.addLine(serviceStateText);

            if (detectedActivities != null) {

                DetectedProbeActivity bestDetectedActivity = detectedActivities.getFirst();
                inboxStyle.addLine(String.format("Activity: %s", bestDetectedActivity.asString()));
                inboxStyle.addLine(String.format("Confidence level: %s (%d%%)",
                        bestDetectedActivity.getConfidenceLevelAsString(), bestDetectedActivity.getConfidence()));
                mLastDetectedProbeActivities = detectedActivities;
            }
            if (receivedLocation != null) {
                inboxStyle.addLine(String.format("Location: (%f, %f)", receivedLocation.getLongitude(), receivedLocation.getLatitude()));
                inboxStyle.addLine(String.format("Provider type: %s (accuracy: %.0fm)", receivedLocation.getProvider(), receivedLocation.getAccuracy()));
                mLastReceivedLocation = receivedLocation;
            }

            builder.setStyle(inboxStyle);
        }

        Intent intent = new Intent(getActivity(), MainActivity.class);
        PendingIntent clickIntent = PendingIntent.getActivity(getActivity(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(clickIntent);

        mNotificationManager.notify(notificationId, builder.build());
    }

    private void updateTokenField() {

        if (mActivity == null) {
            return;
        }

        final BackendStorage storage = BackendStorage.create(mActivity);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView deviceToken = (TextView) mActivity.findViewById(R.id.device_token);
                deviceToken.setText(storage.readDeviceToken().or("?"));
            }
        });



    }

    private boolean isNewServiceRunningState(final String serviceRunningState) {
        if (serviceRunningState == null)
            return false;

        if (mLastServiceRunningState == null)
            return true;

        return !mLastServiceRunningState.equals(serviceRunningState);
    }

    private boolean isNewActivityRecognitionProbeValue(final ActivityDataContainer detectedProbeActivities) {
        if (detectedProbeActivities == null)
            return false;

        if (mLastDetectedProbeActivities == null)
            return true;

        return !(mLastDetectedProbeActivities.equals(detectedProbeActivities));
    }

    private boolean isNewLocationProbeValue(final Location location) {
        if (location == null)
            return false;

        if (mLastReceivedLocation == null)
            return true;

        return Float.compare(mLastReceivedLocation.distanceTo(location), 0F) != 0 || Float.compare(mLastReceivedLocation.getAccuracy(), location.getAccuracy()) != 0;
    }

    private void showToast(String msg) {
        if (msg == null)
            return;

        if (mActivity != null) {
            final String messageText = msg;
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity(), messageText, Toast.LENGTH_SHORT).show();
                }
            });
        }

    }

    private void setButtonStates(final boolean enabled) {
        if (mActivity != null) {
            final Button startButton = (Button) mActivity.findViewById(R.id.config_StartService);
            final Button stopButton = (Button) mActivity.findViewById(R.id.config_StopService);

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final boolean isEnabled = enabled;
                    startButton.setEnabled(isEnabled);
                    stopButton.setEnabled(isEnabled);
                }
            });
        }
    }


    private void startServiceStatusChecking() {
        mServiceStatusChecker.run();;
    }

    private void stopServiceStatusChecking() {
        mHandler.removeCallbacks(mServiceStatusChecker);
        mNotificationManager.cancel(notificationId);
    }
}
