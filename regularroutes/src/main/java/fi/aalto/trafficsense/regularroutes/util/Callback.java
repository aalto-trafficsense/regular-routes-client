package fi.aalto.trafficsense.regularroutes.util;

public interface Callback<T> {
    void run(T result, RuntimeException error);
}
