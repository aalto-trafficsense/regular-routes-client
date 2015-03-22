package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import android.database.sqlite.SQLiteOpenHelper;

import com.google.common.base.Optional;
import com.google.gson.IJsonObject;
import com.google.gson.JsonElement;

import edu.mit.media.funf.probe.Probe;
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
    // commented out WriteDataAction until its operation is fully tested
    //private WriteDataAction mWriteDataAction;

    DataCollector(Listener listener, SQLiteOpenHelper dbHelper) {
        // commented out WriteDataAction until its operation is fully tested
        //mWriteDataAction = new WriteDataAction(dbHelper);
        this.mListener = listener;
    }

    @Override
    public void onDataReceived(IJsonObject probeConfig, IJsonObject data) {

        // commented out WriteDataAction until its operation is fully tested
        //mWriteDataAction.onDataReceived(probeConfig, data);
        ProbeType probeType = ProbeType.fromProbeConfig(probeConfig);
        Timber.d("DataCollector:onDataReceived - data received. Probetype: " + probeType);
        /*
        if (probeType == ProbeType.UNKNOWN)
            Timber.d("Probe config: " + probeConfig);
        */
        switch (probeType) {
            case UNKNOWN:
                return;
            case FUSEDLOCATION:
            case LOCATION:
                mLocationData = LocationData.parseJson(data);
                //Timber.d("Location data parsing succeeded: " + mLocationData.isPresent());
                break;
            case ACTIVITYRECCOGNITION:
                break;
        }
    }

    @Override
    public void onDataCompleted(IJsonObject probeConfig, JsonElement checkpoint) {

        // commented out WriteDataAction until its operation is fully tested
        //mWriteDataAction.onDataCompleted(probeConfig, checkpoint);
        ProbeType probeType = ProbeType.fromProbeConfig(probeConfig);
        Timber.d("DataCollector:onDataCompleted - data completed. Probetype: " + probeType);
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
        else {
            Timber.d("DataCollector:onDataCompleted - data is not ready (it should be)");
        }
    }

    private boolean isDataReady() {
        return mLocationDataComplete;
    }
}
