package fi.aalto.trafficsense.regularroutes;

import android.net.Uri;
import android.os.Handler;
import com.typesafe.config.Config;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.backend.pipeline.DataQueue;
import fi.aalto.trafficsense.regularroutes.backend.rest.RestClient;

public class RegularRoutesConfig {
    public final Uri server;
    public final int queueSize;
    public final int flushThreshold;

    public RegularRoutesConfig(Uri server, int queueSize, int flushThreshold) {
        this.server = server;
        this.queueSize = queueSize;
        this.flushThreshold = flushThreshold;
    }

    public static RegularRoutesConfig create(Config config) {
        return new RegularRoutesConfig(
                Uri.parse(config.getString("server")),
                config.getInt("queue_size"),
                config.getInt("flush_threshold")
        );
    }

    @Override
    public String toString() {
        return "{ server=" + server +
                ", queue_size=" + queueSize +
                ", flush_threshold=" + flushThreshold +
                '}';
    }

    public DataQueue createDataQueue() {
        return new DataQueue(queueSize, flushThreshold);
    }

    public RestClient createRestClient(BackendStorage backendStorage, Handler handler) {
        return new RestClient(server, backendStorage, handler);
    }
}
