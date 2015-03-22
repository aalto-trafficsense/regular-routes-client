package fi.aalto.trafficsense.regularroutes.ui;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Optional;

import java.util.concurrent.atomic.AtomicReference;

import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityDataContainer;
import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityRecognitionProbe;
import fi.aalto.trafficsense.funfprobes.activityrecognition.DetectedProbeActivity;
import fi.aalto.trafficsense.funfprobes.fusedlocation.FusedLocationProbe;
import fi.aalto.trafficsense.regularroutes.R;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.backend.InternalBroadcasts;
import fi.aalto.trafficsense.regularroutes.backend.RegularRoutesPipeline;
import fi.aalto.trafficsense.regularroutes.util.Callback;
import timber.log.Timber;


public class ConfigFragment extends Fragment {
    private final int notificationId = 8001;

    /* Private Members */
    private Activity mActivity;
    private BackendStorage mStorage;
    private Handler mHandler = new Handler();
    private NotificationManager mNotificationManager;
    private ActivityDataContainer mLastDetectedProbeActivities = null;
    private Location mLastReceivedLocation = null;
    private String mLastServiceRunningState = null;
    private AtomicReference<Boolean> mDeviceIdFetchOngoing = new AtomicReference<>(false);
    private AtomicReference<Optional<Integer>> mDeviceId = new AtomicReference<>(Optional.<Integer>absent());


    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case InternalBroadcasts.KEY_DEVICE_ID_FETCH_COMPLETED:
                    // Stop ignoring device id fetches
                    mDeviceIdFetchOngoing.set(false);
                    break;
                case InternalBroadcasts.KEY_SESSION_TOKEN_CLEARED:
                    // Device id for visualization may have been changed due to session change
                    mDeviceId.set(Optional.<Integer>absent());
                    break;
            }
        }
    };


    private Runnable mUiDataUpdater = new Runnable() {
        @Override
        public void run() {
            final int dataChangeCheckInterval = 1000;
            updateUiData();
            mHandler.postDelayed(mUiDataUpdater, dataChangeCheckInterval);
        }
    };

    /* UI Components */
    private Switch mUploadEnabledSwitch;
    private TextView mDeviceIdField;


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
        initFields();
        initButtonHandlers();

    }

    private void initFields() {
        mActivity = getActivity();
        mNotificationManager = (NotificationManager) mActivity.getSystemService(Context.NOTIFICATION_SERVICE);
        mUploadEnabledSwitch = (Switch) mActivity.findViewById(R.id.config_UploadEnabledSwitch);
        mDeviceIdField = (TextView) mActivity.findViewById(R.id.device_id);
        mStorage = BackendStorage.create(mActivity);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        mActivity = getActivity();

        if (mActivity != null) {
            LocalBroadcastManager.getInstance(mActivity).registerReceiver(mBroadcastReceiver, new IntentFilter(InternalBroadcasts.KEY_DEVICE_ID_FETCH_COMPLETED));
        }
        MainActivity mainActivity = (MainActivity)mActivity;

        // Upload enabled state can be changed only when user is signed in
        mUploadEnabledSwitch.setEnabled(mainActivity.isSignedIn());
        Timber.i("Signed in state: " + mainActivity.isSignedIn());

        setDeviceIdFieldValue(mDeviceId.get());
        startUiDataUpdater();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mActivity != null) {
            LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mBroadcastReceiver);
        }

        mDeviceIdFetchOngoing.set(false);
        stopUiDataUpdater();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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

        fetchDeviceId(new Callback<Optional<Integer>>() {
            @Override
            public void run(Optional<Integer> deviceId, RuntimeException error) {
                if (error != null || !deviceId.isPresent()) {
                    showToast("Cannot visualize: Failed to get device id");
                    return;
                }
                else {
                    showToast("Starting visualization...");
                    Uri baseUri = RegularRoutesPipeline.getConfig().server;
                    Uri serviceUri = Uri.withAppendedPath(baseUri, "visualize/" + deviceId.get());
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, serviceUri);
                    mActivity.startActivity(browserIntent);
                }
            }
        });
    }



    private void updateUiData() {
        final String serviceRunningState = getServiceRunningState();
        final ActivityDataContainer detectedActivities = ActivityRecognitionProbe.getLatestDetectedActivities();
        final Location receivedLocation = FusedLocationProbe.getLatestReceivedLocation();

        if (!(isNewServiceRunningState(serviceRunningState) || isNewActivityRecognitionProbeValue(detectedActivities) || isNewLocationProbeValue(receivedLocation)) )
            return; // nothing has been updated

        updateApplicationFields(serviceRunningState, detectedActivities, receivedLocation);
        updateNotification(serviceRunningState, detectedActivities, receivedLocation);

        if (!mDeviceIdFetchOngoing.get() && mStorage.isUserIdAvailable())
            // latter condition applies when user is signed in
            updateDeviceIdField();
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
                        detectedProbeActivity.Confidence);

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

                // Show maximum of 3 best activities
                DetectedProbeActivity bestDetectedActivity = detectedActivities.getFirst();

                inboxStyle.addLine(String.format("Activity #1: %s (%s, %d%%)",
                        bestDetectedActivity.asString(),
                        bestDetectedActivity.getConfidenceLevelAsString(),
                        bestDetectedActivity.Confidence));
                if (detectedActivities.numOfDataEntries() > 1) {
                    DetectedProbeActivity nextDetectedActivity = detectedActivities.get(1);
                    inboxStyle.addLine(String.format("Activity #2: %s (%s, %d%%)",
                            nextDetectedActivity.asString(),
                            nextDetectedActivity.getConfidenceLevelAsString(),
                            nextDetectedActivity.Confidence));
                }
                if (detectedActivities.numOfDataEntries() > 2) {
                    DetectedProbeActivity nextDetectedActivity = detectedActivities.get(2);
                    inboxStyle.addLine(String.format("Activity #3: %s (%s, %d%%)",
                            nextDetectedActivity.asString(),
                            nextDetectedActivity.getConfidenceLevelAsString(),
                            nextDetectedActivity.Confidence));
                }

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

    private void updateDeviceIdField() {
        final Optional<Integer> deviceIdValue = mDeviceId.get();
        if (deviceIdValue.isPresent()) {
            // No need to update anything
            return;
        }

        if (mActivity == null || mDeviceIdFetchOngoing.get()) {
            return;
        }

        // The following value is cleared based on local broadcast message
        mDeviceIdFetchOngoing.set(true);

        fetchDeviceId(new Callback<Optional<Integer>>() {
            @Override
            public void run(Optional<Integer> result, RuntimeException error) {
                final Optional<Integer> deviceId = result;
                final Optional<Integer> oldDeviceId = mDeviceId.get();

                if (!oldDeviceId.isPresent() || !oldDeviceId.get().equals(deviceId.get())) {
                    // value has changed
                    mDeviceId.set(deviceId);
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setDeviceIdFieldValue(deviceId);
                        }
                    });
                }
            }
        });

    }

    private void setDeviceIdFieldValue(Optional<Integer> deviceId) {
        if (deviceId.isPresent()) {
            final String value = deviceId.get().toString();
            if (!mDeviceIdField.getText().equals(value))
                mDeviceIdField.setText(value);

        } else if (!mDeviceIdField.getText().equals("?"))
            mDeviceIdField.setText("?");
    }

    private void fetchDeviceId(Callback<Optional<Integer>> callback) {
        RegularRoutesPipeline.fetchDeviceId(callback);
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
            final Activity activity = mActivity;
            final String messageText = msg;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, messageText, Toast.LENGTH_SHORT).show();
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


    private void startUiDataUpdater() {
        mUiDataUpdater.run();
    }

    private void stopUiDataUpdater() {
        mHandler.removeCallbacks(mUiDataUpdater);
        mNotificationManager.cancel(notificationId);
    }
}
