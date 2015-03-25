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
    private final int dataUpdateInterval = 1000;

    /* Private Members */
    private Activity mActivity;
    private BackendStorage mStorage;
    private Handler mHandler = new Handler();
    private NotificationManager mNotificationManager;
    private ActivityDataContainer mLastDetectedProbeActivities = null;
    private Location mLastReceivedLocation = null;
    private String mLastServiceRunningState = null;
    private AtomicReference<Boolean> mClientNumberFetchOngoing = new AtomicReference<>(false);
    private BroadcastReceiver mBroadcastReceiver;

    private final Runnable mUiDataUpdater = new Runnable() {
        @Override
        public void run() {
            updateUiData();
            mHandler.postDelayed(mUiDataUpdater, dataUpdateInterval);
        }
    };

    private final Runnable mNotificationUpdater = new Runnable() {
        @Override
        public void run() {
            updateNotification();
            mHandler.postDelayed(mNotificationUpdater, dataUpdateInterval);
        }
    };

    /* UI Components */
    private Switch mUploadEnabledSwitch;
    private TextView mClientNumberField;


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

        startNotificationUpdater();
    }

    private void initFields() {
        mActivity = getActivity();
        mNotificationManager = (NotificationManager) mActivity.getSystemService(Context.NOTIFICATION_SERVICE);
        mUploadEnabledSwitch = (Switch) mActivity.findViewById(R.id.config_UploadEnabledSwitch);
        mClientNumberField = (TextView) mActivity.findViewById(R.id.client_number);
        mStorage = BackendStorage.create(mActivity);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        mActivity = getActivity();

        if (mActivity != null) {
            initBroadcastReceiver();
        }
        MainActivity mainActivity = (MainActivity)mActivity;

        // Upload enabled state can be changed only when user is signed in
        mUploadEnabledSwitch.setEnabled(mainActivity.isSignedIn());
        Timber.i("Signed in state: " + mainActivity.isSignedIn());

        setClientNumberFieldValue(mStorage.readClientNumber());
        startUiDataUpdater();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mActivity != null) {
            LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mBroadcastReceiver);
        }

        mClientNumberFetchOngoing.set(false);
        stopUiDataUpdater();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopNotificationUpdater();
    }


    /* Private Helper Methods */
    private void initBroadcastReceiver() {
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                final Activity activity = getActivity();

                switch (action) {
                    case InternalBroadcasts.KEY_CLIENT_NUMBER_FETCH_COMPLETED:
                        // Stop ignoring device id fetches
                        final Optional<Integer> clientNum = mStorage.readClientNumber();
                        if (activity != null)
                        {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setClientNumberFieldValue(clientNum);
                                }
                            });
                        }
                        mClientNumberFetchOngoing.set(false);
                        break;
                    case InternalBroadcasts.KEY_SESSION_TOKEN_CLEARED:
                        // Client number may be cleared
                        final Optional<Integer> clientNumber = mStorage.readClientNumber();
                        if (activity != null)
                        {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setClientNumberFieldValue(clientNumber);
                                }
                            });
                        }

                        break;
                    case InternalBroadcasts.KEY_UPLOAD_SUCCEEDED:
                        if (activity != null)
                        {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(activity.getBaseContext(), "Uploaded data successfully", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        break;
                }
            }
        };

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(InternalBroadcasts.KEY_CLIENT_NUMBER_FETCH_COMPLETED);
        intentFilter.addAction(InternalBroadcasts.KEY_SESSION_TOKEN_CLEARED);
        intentFilter.addAction(InternalBroadcasts.KEY_UPLOAD_SUCCEEDED);

        LocalBroadcastManager.getInstance(mActivity).registerReceiver(mBroadcastReceiver, intentFilter);
    }

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

        fetchClientNumber(new Callback<Optional<Integer>>() {
            @Override
            public void run(Optional<Integer> deviceId, RuntimeException error) {
                if (error != null || !deviceId.isPresent()) {
                    showToast("Cannot visualize: Failed to get device id");
                    return;
                } else {
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
        final DataSnapshot dataSnapshot = new DataSnapshot();

        if (!isDataChanged(dataSnapshot))
            return; // nothing has been updated

        updateApplicationFields(dataSnapshot);

        if (!mClientNumberFetchOngoing.get() && mStorage.isUserIdAvailable())
            // latter condition applies when user is signed in
            updateDeviceIdField();
    }

    private void updateNotification() {
        final DataSnapshot dataSnapshot = new DataSnapshot();

        if (!isDataChanged(dataSnapshot))
            return; // nothing has been updated

        updateNotification(dataSnapshot);
    }



    private void updateApplicationFields(final DataSnapshot dataSnapshot) {

        if (mActivity == null)
            return;

        final TextView txtStatus = (TextView)mActivity.findViewById(R.id.config_serviceStatus);


        if (txtStatus != null) {
            txtStatus.setText(dataSnapshot.ServiceRunningState);
        }

        if (dataSnapshot.DetectedActivities != null) {
            final DetectedProbeActivity detectedProbeActivity = dataSnapshot.DetectedActivities.getFirst();
            final TextView txtActivity = (TextView)mActivity.findViewById(R.id.config_activity);
            if (txtActivity != null) {
                String txt = String.format("%s (confidence: %s, %d%%)",
                        detectedProbeActivity.asString(),
                        detectedProbeActivity.getConfidenceLevelAsString(),
                        detectedProbeActivity.Confidence);

                txtActivity.setText(txt);
            }


        }

        if (dataSnapshot.ReceivedLocation != null) {
            final TextView txtLocation =
                    (TextView)mActivity.findViewById(R.id.config_location);
            final TextView txtLocation_row2 =
                    (TextView)mActivity.findViewById(R.id.config_location_row2);
            if (txtLocation != null) {
                String txt = String.format("(%f, %f)",
                        dataSnapshot.ReceivedLocation.getLongitude(),
                        dataSnapshot.ReceivedLocation.getLatitude());
                txtLocation.setText(txt);
            }
            if (txtLocation_row2 != null) {
                String txt = String.format("Accuracy: %.0fm",
                        dataSnapshot.ReceivedLocation.getAccuracy());
                txtLocation_row2.setText(txt);
            }
        }

        updateUploadSwitchState();
    }

    private void updateNotification(final DataSnapshot dataSnapshot) {
        final String title = "Regular Routes Client";
        final String serviceStateText = getString(R.string.config_serviceRunningLabel) +
                dataSnapshot.ServiceRunningState;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getActivity())
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(serviceStateText)
                .setOngoing(true);

        if (dataSnapshot.DetectedActivities != null || dataSnapshot.ReceivedLocation != null) {
            // Use big style (multiple rows)
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle(title);
            inboxStyle.addLine(serviceStateText);

            if (dataSnapshot.DetectedActivities != null) {

                // Show maximum of 3 best activities
                DetectedProbeActivity bestDetectedActivity =
                        dataSnapshot.DetectedActivities.getFirst();

                inboxStyle.addLine(String.format("Activity #1: %s (%s, %d%%)",
                        bestDetectedActivity.asString(),
                        bestDetectedActivity.getConfidenceLevelAsString(),
                        bestDetectedActivity.Confidence));
                if (dataSnapshot.DetectedActivities.numOfDataEntries() > 1) {
                    DetectedProbeActivity nextDetectedActivity =
                            dataSnapshot.DetectedActivities.get(1);
                    inboxStyle.addLine(String.format("Activity #2: %s (%s, %d%%)",
                            nextDetectedActivity.asString(),
                            nextDetectedActivity.getConfidenceLevelAsString(),
                            nextDetectedActivity.Confidence));
                }
                if (dataSnapshot.DetectedActivities.numOfDataEntries() > 2) {
                    DetectedProbeActivity nextDetectedActivity =
                            dataSnapshot.DetectedActivities.get(2);
                    inboxStyle.addLine(String.format("Activity #3: %s (%s, %d%%)",
                            nextDetectedActivity.asString(),
                            nextDetectedActivity.getConfidenceLevelAsString(),
                            nextDetectedActivity.Confidence));
                }

                mLastDetectedProbeActivities = dataSnapshot.DetectedActivities;
            }
            if (dataSnapshot.ReceivedLocation != null) {
                inboxStyle.addLine(String.format("Location: (%f, %f)",
                        dataSnapshot.ReceivedLocation.getLongitude(),
                        dataSnapshot.ReceivedLocation.getLatitude()));
                inboxStyle.addLine(String.format("Provider type: %s (accuracy: %.0fm)",
                        dataSnapshot.ReceivedLocation.getProvider(),
                        dataSnapshot.ReceivedLocation.getAccuracy()));
                mLastReceivedLocation = dataSnapshot.ReceivedLocation;
            }

            builder.setStyle(inboxStyle);
        }

        Intent intent = new Intent(getActivity(), MainActivity.class);
        PendingIntent clickIntent = PendingIntent.getActivity(getActivity(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(clickIntent);

        mNotificationManager.notify(notificationId, builder.build());
    }

    private void updateDeviceIdField() {
        final Optional<Integer> clientNumberValue = mStorage.readClientNumber();
        if (clientNumberValue.isPresent()) {
            // No need to update anything
            return;
        }

        if (mActivity == null || mClientNumberFetchOngoing.get()) {
            return;
        }

        // The following value is cleared based on local broadcast message
        mClientNumberFetchOngoing.set(true);

        fetchClientNumber(new Callback<Optional<Integer>>() {
            @Override
            public void run(Optional<Integer> result, RuntimeException error) {
                final Optional<Integer> deviceId = result;
                final Optional<Integer> oldDeviceId = mStorage.readClientNumber();

                if (!oldDeviceId.isPresent() || !oldDeviceId.get().equals(deviceId.get())) {
                    // value has changed
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setClientNumberFieldValue(deviceId);
                        }
                    });
                }
            }
        });

    }

    private void setClientNumberFieldValue(Optional<Integer> clientNumber) {
        if (clientNumber.isPresent()) {
            final String value = clientNumber.get().toString();
            if (!mClientNumberField.getText().equals(value))
                mClientNumberField.setText(value);

        } else if (!mClientNumberField.getText().equals("?"))
            mClientNumberField.setText("?");
    }

    private void fetchClientNumber(Callback<Optional<Integer>> callback) {
        RegularRoutesPipeline.fetchClientNumber(callback);
    }

    private boolean isDataChanged(DataSnapshot dataSnapshot) {
        return
                isNewServiceRunningState(dataSnapshot.ServiceRunningState)
                        || isNewActivityRecognitionProbeValue(dataSnapshot.DetectedActivities)
                        || isNewLocationProbeValue(dataSnapshot.ReceivedLocation);
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

    }

    private void startNotificationUpdater() {
        mNotificationUpdater.run();
    }

    private void stopNotificationUpdater() {
        mHandler.removeCallbacks(mNotificationUpdater);
        mNotificationManager.cancel(notificationId);
    }

    /* Helper Class to capture current value of data sources */
    private class DataSnapshot {
        public final String ServiceRunningState;
        public final ActivityDataContainer DetectedActivities;
        public final Location ReceivedLocation;

        public DataSnapshot()
        {
            ServiceRunningState = getServiceRunningState();
            DetectedActivities = ActivityRecognitionProbe.getLatestDetectedActivities();
            ReceivedLocation = FusedLocationProbe.getLatestReceivedLocation();
        }
    }
}
