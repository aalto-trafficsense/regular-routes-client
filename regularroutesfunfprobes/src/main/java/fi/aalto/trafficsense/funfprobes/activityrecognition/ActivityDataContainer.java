package fi.aalto.trafficsense.funfprobes.activityrecognition;


import java.util.ArrayList;
import java.util.List;

/**
 * Class that contains activity data that is sent to server per one received activity input
 **/
public class ActivityDataContainer {
    private final List<DetectedProbeActivity> mData = new ArrayList<>();

    public ActivityDataContainer() {

    }
    public ActivityDataContainer(int activityType, int confidence) {
        add(new DetectedProbeActivity(activityType, confidence));
    }

    public void add(DetectedProbeActivity activity) {
        mData.add(activity);
    }

    public int numOfDataEntries() {
        return mData.size();
    }

    public DetectedProbeActivity getFirst() {
        if (mData.size() < 1)
            return null;

        return get(0);
    }

    public DetectedProbeActivity get(int i) {
        return mData.get(i);
    }

    public boolean equals(ActivityDataContainer other) {
        if (other == null && numOfDataEntries() != other.numOfDataEntries())
            return false;


        for(int i = 0; i < numOfDataEntries(); ++i) {
            if (!get(i).equals(other.get(i)))
                return false;
        }

        return true;
    }

    public List<DetectedProbeActivity> getAll() {
        return new ArrayList<>(mData);
    }
}
