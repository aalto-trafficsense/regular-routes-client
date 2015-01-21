package fi.aalto.trafficsense.regularroutes.ui;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.DetectedActivity;

import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityRecognitionProbe;
import fi.aalto.trafficsense.funfprobes.activityrecognition.DetectedProbeActivity;
import fi.aalto.trafficsense.regularroutes.R;
import timber.log.Timber;


public class ConfigFragment extends Fragment {
    /* Private Members */
    private Activity mActivity;
    private Handler mHandler = new Handler();
    private final int notificationId = 8001;
    private NotificationManager mNotificationManager;


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

    private void initButtonHandlers() {
        if (mActivity != null) {
            final Button startButton = (Button) mActivity.findViewById(R.id.config_StartService);
            final Button stopButton = (Button) mActivity.findViewById(R.id.config_StopService);
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
        }
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
        String serviceRunningState = getServiceRunningState();
        final TextView txtStatus = (TextView)getActivity().findViewById(R.id.config_serviceStatus);
        if (txtStatus != null) {
            txtStatus.setText(serviceRunningState);
        }
        else
            Timber.d("Failed to update service running state");

        final String title = "Regular Routes Client";
        final String serviceStateText = getString(R.string.config_serviceRunningLbl) + getServiceRunningState();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getActivity())
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(serviceStateText)
                .setOngoing(true);

        // Big Style
        final DetectedProbeActivity detectedActivity = ActivityRecognitionProbe.getLatestDetectedActivity();
        if (detectedActivity != null) {
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle(title);
            inboxStyle.addLine(serviceStateText);
            inboxStyle.addLine("Activity: " + detectedActivity.asString());
            inboxStyle.addLine("Confidence level: " + detectedActivity.getConfidence());
            builder.setStyle(inboxStyle);
        }



        Intent intent = new Intent(getActivity(), MainActivity.class);
        PendingIntent clickIntent = PendingIntent.getActivity(getActivity(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(clickIntent);

        mNotificationManager.notify(notificationId, builder.build());


    }

    private void showToast(String msg) {
        if (msg == null)
            return;

        if (mActivity != null) {
            final String messageText = msg;
            getActivity().runOnUiThread(new Runnable() {
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
