package fi.aalto.trafficsense.funfprobes.activityrecognition;


import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class that contains activity data that is sent to server per one received activity input
 **/

public final class ActivityDataContainer {
    @SerializedName("activities")
    public final ImmutableCollection<DetectedProbeActivity> Activities;

    public ActivityDataContainer() {
        Activities = ImmutableList.of();
    }

    public ActivityDataContainer(int activityType, int confidence) {
        DetectedProbeActivity[] activities = new DetectedProbeActivity[]{new DetectedProbeActivity(activityType, confidence)};
        Activities = ImmutableList.copyOf(activities);
    }

    public ActivityDataContainer(Map<Integer, Integer> activityConfidenceMap) {
        ArrayList<DetectedProbeActivity> activities = new ArrayList<>();
        for (Integer key : activityConfidenceMap.keySet())
            activities.add(new DetectedProbeActivity(key, activityConfidenceMap.get(key)));

        Activities = ImmutableList.copyOf(activities);

    }

    public int numOfDataEntries() {
        return Activities.size();
    }

    public DetectedProbeActivity getFirst() {
        if (Activities.size() < 1)
            return null;

        return get(0);
    }

    public DetectedProbeActivity get(int i) {
        return Activities.asList().get(i);
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
        return Activities.asList();
    }

    @Override
    public String toString() {
        StringBuilder activities = new StringBuilder();
        List<DetectedProbeActivity> list  = getAll();
        for (int i = 0; i < list.size(); ++i) {
            DetectedProbeActivity a = list.get(i);
            if (i >= 0)
                activities.append(", ");

            activities.append("{mActivityType=" + a.Type + ", mConfidence=" + a.Confidence + "}");

        }

        return String.format("[%s]", activities.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActivityDataContainer that = (ActivityDataContainer) o;

        if (Activities.size() != that.Activities.size())
            return false;

        List<DetectedProbeActivity> list1  = getAll();
        List<DetectedProbeActivity> list2  = that.getAll();
        for (int i = 0; i < list1.size(); ++i)
            if (!list1.get(i).equals(list2.get(i)))
                return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = 0;
        long temp;
        List<DetectedProbeActivity> list  = getAll();
        for (int i = 0; i < list.size(); ++i) {
            temp = (long)list.get(i).hashCode();
            result = 31 * result + (int) (temp ^ (temp >>> 32));
        }

        return result;
    }
}
