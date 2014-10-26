package fi.aalto.trafficsense.regularroutes.backend.parser;

import com.google.common.base.Objects;
import com.google.gson.IJsonObject;
import edu.mit.media.funf.probe.builtin.SimpleLocationProbe;

public enum ProbeType {
    UNKNOWN(null), LOCATION(SimpleLocationProbe.class.getName());

    private static final String KEY_TYPE = "@type";

    private final String mClassName;

    private ProbeType(String className) {
        this.mClassName = className;
    }

    public static ProbeType fromProbeConfig(IJsonObject probeConfig) {
        String type = (!probeConfig.has(KEY_TYPE)) ? null : probeConfig.get(KEY_TYPE).getAsString();
        for (ProbeType probeType : ProbeType.values()) {
            if (Objects.equal(probeType.mClassName, type)) {
                return probeType;
            }
        }
        return ProbeType.UNKNOWN;
    }
}
