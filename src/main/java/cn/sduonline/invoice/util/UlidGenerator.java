package cn.sduonline.invoice.util;

import java.security.SecureRandom;

/** Generates canonical 26-character Crockford Base32 ULIDs. */
public final class UlidGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private UlidGenerator() {
    }

    public static String next() {
        char[] value = new char[26];
        long time = System.currentTimeMillis();
        for (int i = 9; i >= 0; i--) {
            value[i] = ALPHABET[(int) (time & 31)];
            time >>>= 5;
        }
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        int buffer = 0;
        int bits = 0;
        int source = 0;
        for (int i = 10; i < value.length; i++) {
            while (bits < 5) {
                buffer = (buffer << 8) | (random[source++] & 0xff);
                bits += 8;
            }
            bits -= 5;
            value[i] = ALPHABET[(buffer >>> bits) & 31];
        }
        return new String(value);
    }
}
