package fi.aalto.trafficsense.regularroutes.backend.pipeline;

import com.google.common.base.Optional;
import com.google.gson.IJsonObject;
import com.google.gson.JsonElement;
import edu.mit.media.funf.probe.Probe;
import fi.aalto.trafficsense.regularroutes.backend.parser.LocationData;
import fi.aalto.trafficsense.regularroutes.backend.parser.ProbeType;
import timber.log.Timber;

public final class DataCollector implements Probe.DataListener {
    private Optional<LocationData> mLocationData = Optional.absent();
    private boolean mLocationDataComplete;

    @Override
    public void onDataReceived(IJsonObject probeConfig, IJsonObject data) {
        ProbeType probeType = ProbeType.fromProbeConfig(probeConfig);
        switch (probeType) {
            case UNKNOWN:
                return;
            case LOCATION:
                mLocationDataComplete = false;
                mLocationData = LocationData.parseJson(data);
                break;
        }
    }

    @Override
    public void onDataCompleted(IJsonObject probeConfig, JsonElement checkpoint) {
        ProbeType probeType = ProbeType.fromProbeConfig(probeConfig);
        switch (probeType) {
            case UNKNOWN:
                return;
            case LOCATION:
                mLocationDataComplete = mLocationData.isPresent();
                break;
        }

        if (isDataReady()) {
            Timber.d("%s", mLocationData);
        }
    }

    private boolean isDataReady() {
        return mLocationDataComplete;
    }
}
