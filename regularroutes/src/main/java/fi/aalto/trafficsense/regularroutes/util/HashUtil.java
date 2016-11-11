package fi.aalto.trafficsense.regularroutes.util;


import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    /** Convert Byte array to Hex (string) */
    // From: https://stackoverflow.com/a/9855338
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /** Create Hash (string) value from id string by using SHA-256 hash algorithm
     * Return null if id is null or functionality is not supported by JVM
     **/
    public static String getHashStringFromId(String id) {
        if (id == null)
            return null;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(id.getBytes("UTF-8"));
            byte[] digest = md.digest();

            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return null;
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }
}
