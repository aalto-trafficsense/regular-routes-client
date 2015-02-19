package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import android.database.sqlite.SQLiteOpenHelper;

import com.google.common.base.Optional;
import com.google.gson.IJsonObject;
import com.google.gson.JsonElement;

import edu.mit.media.funf.probe.Probe;
import edu.mit.media.funf.action.WriteDataAction;
import fi.aalto.trafficsense.funfprobes.activityrecognition.ActivityRecognitionProbe;
import fi.aalto.trafficsense.regularroutes.backend.parser.DataPacket;
import fi.aalto.trafficsense.regularroutes.backend.parser.LocationData;
import fi.aalto.trafficsense.regularroutes.backend.parser.ProbeType;
import timber.log.Timber;

/*
Re-factored DataCollector to have instance of Funf's WriteDataAction in private field, and used functionality in it to
also write probe data into Funf SQLite database. In consequence, all overriden methods call the corresponding method in
private instance first.
*/
public final class DataCollector implements Probe.DataListener {
    public interface Listener {
        void onDataReady(DataPacket data);
    }

    private final Listener mListener;
    private Optional<LocationData> mLocationData = Optional.absent();
    private boolean mLocationDataComplete;
    private WriteDataAction mWriteDataAction;

    DataCollector(Listener listener, SQLiteOpenHelper dbHelper) {
        mWriteDataAction = new WriteDataAction(dbHelper);
        this.mListener = listener;
    }

    @Override
    public void onDataReceived(IJsonObject probeConfig, IJsonObject data) {

        mWriteDataAction.onDataReceived(probeConfig, data);
        ProbeType probeType = ProbeType.fromProbeConfig(probeConfig);
        //Timber.d("DataCollector:onDataReceived - data received. Probetype: " + probeType);
        if (probeType == ProbeType.UNKNOWN)
            Timber.d("Probe config: " + probeConfig);
        switch (probeType) {
            case UNKNOWN:
                return;
            case FUSEDLOCATION:
                Timber.d("DataCollector:onDataReceived - Fused location data received");
            case LOCATION:
                mLocationData = LocationData.parseJson(data);
                Timber.d("Location data parsing succeeded: " + mLocationData.isPresent());
                break;
            case ACTIVITYRECCOGNITION:
                Timber.d("Activity recognition data received: Ignoring for now...");
                break;
        }
    }

    @Override
    public void onDataCompleted(IJsonObject probeConfig, JsonElement checkpoint) {

        mWriteDataAction.onDataCompleted(probeConfig, checkpoint);
        ProbeType probeType = ProbeType.fromProbeConfig(probeConfig);
        switch (probeType) {
            case UNKNOWN:
                return;
            case FUSEDLOCATION:
            case LOCATION:
                mLocationDataComplete = mLocationData.isPresent();
                break;
            case ACTIVITYRECCOGNITION:

                break;
        }

        if (isDataReady()) {
            mListener.onDataReady(new DataPacket(mLocationData.get(), ActivityRecognitionProbe.getLatestDetectedActivities()));
            mLocationDataComplete = false;
        }
    }

    private boolean isDataReady() {
        return mLocationDataComplete;
    }
}
