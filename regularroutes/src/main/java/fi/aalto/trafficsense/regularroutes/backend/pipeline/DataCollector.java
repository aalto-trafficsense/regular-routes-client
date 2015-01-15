package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import com.google.common.base.Optional;
import com.google.gson.IJsonObject;
import com.google.gson.JsonElement;
import edu.mit.media.funf.probe.Probe;
import fi.aalto.trafficsense.regularroutes.backend.parser.LocationData;
import fi.aalto.trafficsense.regularroutes.backend.parser.ProbeType;
import timber.log.Timber;

public final class DataCollector implements Probe.DataListener {
    public interface Listener {
        void onDataReady(LocationData locationData);
    }

    private final Listener mListener;
    private Optional<LocationData> mLocationData = Optional.absent();
    private boolean mLocationDataComplete;

    DataCollector(Listener listener) {
        this.mListener = listener;
    }

    @Override
    public void onDataReceived(IJsonObject probeConfig, IJsonObject data) {

        ProbeType probeType = ProbeType.fromProbeConfig(probeConfig);
        Timber.d("DataCollector:onDataReceived - data received. Probetype: " + probeType);
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
            case ACTIVITY:
                Timber.d("Activity data received: Ignoring for now...");
                break;
        }
    }

    @Override
    public void onDataCompleted(IJsonObject probeConfig, JsonElement checkpoint) {
        ProbeType probeType = ProbeType.fromProbeConfig(probeConfig);
        switch (probeType) {
            case UNKNOWN:
                return;
            case FUSEDLOCATION:
                Timber.d("DataCollector:onDataCompleted - Fused location data completed");
            case LOCATION:
                mLocationDataComplete = mLocationData.isPresent();
                break;
            case ACTIVITY:

                break;
        }

        if (isDataReady()) {
            mListener.onDataReady(mLocationData.get());

            mLocationDataComplete = false;
        }
    }

    private boolean isDataReady() {
        return mLocationDataComplete;
    }
}
