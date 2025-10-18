package vega.vcs.shared.util;

/**
 * Hexadecimal utility class
 * Provides methods for converting bytes to hex strings
 * Follows Single Responsibility Principle
 */
public class HexUtils {

    private HexUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts byte array to hexadecimal string
     * @param bytes the byte array to convert
     * @return hexadecimal string representation
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * Converts hexadecimal string to byte array
     * @param hex the hexadecimal string to convert
     * @return byte array representation
     */
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }


}
