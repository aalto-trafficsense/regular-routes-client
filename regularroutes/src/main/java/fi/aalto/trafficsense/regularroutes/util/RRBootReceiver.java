package fi.aalto.trafficsense.regularroutes.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import fi.aalto.trafficsense.regularroutes.ui.LoginActivity;
import fi.aalto.trafficsense.regularroutes.ui.MainActivity;
import timber.log.Timber;

/**
 * Created by mikko.rinne@aalto.fi on 14/12/15.
 */
public class RRBootReceiver extends BroadcastReceiver
{

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.d("RRBootReceiver called!");
        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)){
            Timber.d("Starting main activity");
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(mainIntent);
            /* Intent loginIntent = new Intent(context, LoginActivity.class);
            loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(loginIntent); */
        }
    }

}