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
import com.google.common.collect.UnmodifiableIterator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityDataContainer;
import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityRecognitionProbe;
import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityType;
import fi.aalto.trafficsense.funfprobes.activityrecognition.DetectedProbeActivity;
import fi.aalto.trafficsense.funfprobes.fusedlocation.FusedLocationProbe;
import fi.aalto.trafficsense.regularroutes.R;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.backend.InternalBroadcasts;
import fi.aalto.trafficsense.regularroutes.backend.RegularRoutesPipeline;
import fi.aalto.trafficsense.regularroutes.backend.pipeline.PipelineThread;
import fi.aalto.trafficsense.regularroutes.backend.rest.RestClient;
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
    private ActivityDataContainer mLatestDetectedProbeActivities = null;
    private Location mLatestReceivedLocation = null;
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
    private TextView mActivityTextField;
    private TextView mServiceStatusTextField;
    private TextView mLocationTextField;
    private TextView mLocationTextField2;
    private TextView mLatestUploadTextField;
    private TextView mQueueLengthTextField;


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
        mActivityTextField  = (TextView)mActivity.findViewById(R.id.config_activity);
        mLocationTextField = (TextView)mActivity.findViewById(R.id.config_location);
        mLocationTextField2 = (TextView)mActivity.findViewById(R.id.config_location_row2);
        mServiceStatusTextField = (TextView)mActivity.findViewById(R.id.config_serviceStatus);
        mLatestUploadTextField  = (TextView)mActivity.findViewById(R.id.LatestUpload);
        mQueueLengthTextField  = (TextView)mActivity.findViewById(R.id.QueueLength);
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
                }
            }
        };

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(InternalBroadcasts.KEY_CLIENT_NUMBER_FETCH_COMPLETED);
        intentFilter.addAction(InternalBroadcasts.KEY_SESSION_TOKEN_CLEARED);

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
                    if (isRunning) {
                        if (ActivityRecognitionProbe.isSleepActive()) return "Sleeping";
                        else return "Running";
                    } else
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
        else {
            if (!RegularRoutesPipeline.flushDataQueueToServer())
                Timber.e("ConfigFragment: Stopping service, but failed to flush collected data to server");
            if (setServiceRunning(false)) {
                stopped = true;
                showToast("Background service stopping");
            }
            else {
                showToast("Background service stopping failed");
            }
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
            public void run(Optional<Integer> clientNumber, RuntimeException error) {
                if (error != null || !clientNumber.isPresent()) {
                    showToast("Cannot visualize: Failed to get client number");
                    final String err = error != null ? error.getMessage() : "";
                    Timber.w("Failed to get client number: " + err);
                    return;
                } else {
                    showToast("Starting visualization...");
                    // MJR: Use the /dev server for visualization
                    String serverString = RegularRoutesPipeline.getConfig().server.toString().replace("/api","/dev");
                    // Uri baseUri = RegularRoutesPipeline.getConfig().server.toString().replace("/api","/dev");
                    Uri serviceUri = Uri.withAppendedPath(Uri.parse(serverString), "visualize/" + clientNumber.get());
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

        if (mServiceStatusTextField != null) {
            mServiceStatusTextField.setText(dataSnapshot.ServiceRunningState);
        }

        if (dataSnapshot.DetectedActivities != null) {
            UnmodifiableIterator<DetectedProbeActivity> iter = dataSnapshot.DetectedActivities.Activities.iterator();
            if (iter.hasNext()) {
                DetectedProbeActivity activityToShowInUi = iter.next();

                // skip "ON_FOOT" types
                while (activityToShowInUi.Type == ActivityType.ON_FOOT && iter.hasNext()) {
                    activityToShowInUi = iter.next();
                }

                if (mActivityTextField != null) {
                    String txt = String.format("%s (confidence: %s, %d%%)",
                            activityToShowInUi.asString(),
                            activityToShowInUi.getConfidenceLevelAsString(),
                            activityToShowInUi.Confidence);

                    mActivityTextField.setText(txt);
                }
            }
        }

        if (dataSnapshot.ReceivedLocation != null) {

            if (mLocationTextField != null) {
                String txt = String.format("(%f, %f)",
                        dataSnapshot.ReceivedLocation.getLongitude(),
                        dataSnapshot.ReceivedLocation.getLatitude());
                mLocationTextField.setText(txt);
            }
            if (mLocationTextField2 != null) {
                String txt = String.format("Accuracy: %.0fm (%s)",
                        dataSnapshot.ReceivedLocation.getAccuracy(),
                        dataSnapshot.ReceivedLocation.getProvider());
                mLocationTextField2.setText(txt);
            }
        }

        mQueueLengthTextField.setText(String.format("%d", dataSnapshot.QueueLength));
        mLatestUploadTextField.setText(dataSnapshot.LatestUploadTime);

        updateUploadSwitchState();
    }

    private void updateNotification(final DataSnapshot dataSnapshot) {
        final String title = "Regular Routes Client";
        final String serviceStateText = getString(R.string.config_serviceRunningLabel) +
                dataSnapshot.ServiceRunningState;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getActivity())
                .setSmallIcon(R.drawable.ic_launcher) /* default icon */
                .setContentTitle(title)
                .setContentText(serviceStateText)
                .setOngoing(true);


        if (dataSnapshot.DetectedActivities != null || dataSnapshot.ReceivedLocation != null) {
            // Use big style (multiple rows)
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle(title);
            inboxStyle.addLine(serviceStateText);

            if (dataSnapshot.DetectedActivities != null) {

                // Show maximum of 3 best activities excluding ON_FOOT
                UnmodifiableIterator<DetectedProbeActivity> iter = dataSnapshot.DetectedActivities.Activities.iterator();
                if (iter.hasNext()) {
                    DetectedProbeActivity bestDetectedActivity = iter.next();
                    while (bestDetectedActivity.Type == ActivityType.ON_FOOT && iter.hasNext())
                        bestDetectedActivity = iter.next();

                    /* Icon is set based on most confident detected activity */
                    builder.setSmallIcon(getActivityIconId(bestDetectedActivity));

                    inboxStyle.addLine(String.format("Activity #1: %s (%s, %d%%)",
                            bestDetectedActivity.asString(),
                            bestDetectedActivity.getConfidenceLevelAsString(),
                            bestDetectedActivity.Confidence));

                    if (iter.hasNext()) {
                        DetectedProbeActivity secondDetectedActivity = iter.next();
                        while (secondDetectedActivity.Type == ActivityType.ON_FOOT && iter.hasNext())
                            secondDetectedActivity = iter.next();

                        inboxStyle.addLine(String.format("Activity #2: %s (%s, %d%%)",
                                secondDetectedActivity.asString(),
                                secondDetectedActivity.getConfidenceLevelAsString(),
                                secondDetectedActivity.Confidence));
                    }
                    if (iter.hasNext()) {

                        DetectedProbeActivity thirdDetectedActivity = iter.next();
                        while (thirdDetectedActivity.Type == ActivityType.ON_FOOT && iter.hasNext())
                            thirdDetectedActivity = iter.next();

                        inboxStyle.addLine(String.format("Activity #3: %s (%s, %d%%)",
                                thirdDetectedActivity.asString(),
                                thirdDetectedActivity.getConfidenceLevelAsString(),
                                thirdDetectedActivity.Confidence));
                    }
                }

                mLatestDetectedProbeActivities = dataSnapshot.DetectedActivities;
            }
            if (dataSnapshot.ReceivedLocation != null) {
                inboxStyle.addLine(String.format("Location: (%f, %f)",
                        dataSnapshot.ReceivedLocation.getLongitude(),
                        dataSnapshot.ReceivedLocation.getLatitude()));
                inboxStyle.addLine(String.format("Provider type: %s (accuracy: %.0fm)",
                        dataSnapshot.ReceivedLocation.getProvider(),
                        dataSnapshot.ReceivedLocation.getAccuracy()));
                mLatestReceivedLocation = dataSnapshot.ReceivedLocation;
            }

            builder.setStyle(inboxStyle);
        }

        Intent intent = new Intent(getActivity(), MainActivity.class);
        PendingIntent clickIntent = PendingIntent.getActivity(getActivity(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(clickIntent);

        mNotificationManager.notify(notificationId, builder.build());
    }

    /**
     * Get Icon resource id based on detected activity type (activity recognition)
     **/
    private int getActivityIconId(DetectedProbeActivity bestDetectedActivity) {
        switch (bestDetectedActivity.Type) {

            case IN_VEHICLE:
                return R.drawable.ic_activity_in_vehicle;
            case ON_BICYCLE:
                return R.drawable.ic_activity_on_bicycle;
            case ON_FOOT:
                return R.drawable.ic_activity_walking;
            case RUNNING:
                return R.drawable.ic_activity_running;
            case STILL:
                return R.drawable.ic_activity_still;
            case TILTING:
                return R.drawable.ic_activity_tilting;
            case UNKNOWN:
                return R.drawable.ic_activity_unknown;
            case WALKING:
                return R.drawable.ic_activity_walking;
        }

        return R.drawable.ic_activity_unknown;
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
                        || isNewLocationProbeValue(dataSnapshot.ReceivedLocation)
                        || isNewQueueLength(dataSnapshot.QueueLength)
                        || isNewLatestUploadTime(dataSnapshot.LatestUploadTime);
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

        if (mLatestDetectedProbeActivities == null)
            return true;

        return !(mLatestDetectedProbeActivities.equals(detectedProbeActivities));
    }

    private boolean isNewLocationProbeValue(final Location location) {
        if (location == null)
            return false;

        if (mLatestReceivedLocation == null)
            return true;

        return Float.compare(mLatestReceivedLocation.distanceTo(location), 0F) != 0 || Float.compare(mLatestReceivedLocation.getAccuracy(), location.getAccuracy()) != 0;
    }

    private boolean isNewQueueLength(final int QueueLength) {
        return QueueLength == RegularRoutesPipeline.queueSize();
    }

    private boolean isNewLatestUploadTime(final String LatestUpload) { return LatestUpload.equals(RestClient.getLatestUploadTime()); }

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
        public final int QueueLength;
        public final String LatestUploadTime;

        public DataSnapshot()
        {
            ServiceRunningState = getServiceRunningState();
            DetectedActivities = ActivityRecognitionProbe.getLatestDetectedActivities();
            ReceivedLocation = FusedLocationProbe.getLatestReceivedLocation();
            QueueLength = RegularRoutesPipeline.queueSize();
            LatestUploadTime = RestClient.getLatestUploadTime();
        }
    }


}
