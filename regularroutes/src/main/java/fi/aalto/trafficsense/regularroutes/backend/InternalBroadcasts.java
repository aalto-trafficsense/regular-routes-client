package fi.aalto.trafficsense.regularroutes.backend;


/**
 * Class that has internal broadcast types defined to be used for internal notifications
 * of asynchronous actions
 **/
public class InternalBroadcasts {
    public static final String KEY_AUTH_TOKEN_CLEARED = "AUTH_TOKEN_CLEARED";
    public static final String KEY_AUTH_TOKEN_SET = "AUTH_TOKEN_SET";
    public static final String KEY_ONE_TIME_TOKEN_CLEARED = "ONE_TIME_TOKEN_CLEARED";
    public static final String KEY_ONE_TIME_TOKEN_SET = "ONE_TIME_TOKEN_SET";

    public static final String KEY_SERVER_CONNECTION_FAILURE = "SERVER_CONNECTION_FAILURE";
    public static final String KEY_SERVER_CONNECTION_SUCCEEDED = "SERVER_CONNECTION_SUCCEEDED";
    public static final String KEY_REGISTRATION_SUCCEEDED = "REGISTRATION_SUCCEEDED";
    public static final String KEY_AUTHENTICATION_SUCCEEDED = "AUTHENTICATION_SUCCEEDED";
    public static final String KEY_AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";

}
