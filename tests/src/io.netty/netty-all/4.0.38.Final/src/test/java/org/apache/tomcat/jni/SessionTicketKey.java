/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tomcat.jni;

public final class SessionTicketKey {
    public static final int NAME_SIZE = 16;
    public static final int HMAC_KEY_SIZE = 16;
    public static final int AES_KEY_SIZE = 16;
    public static final int TICKET_KEY_SIZE = NAME_SIZE + HMAC_KEY_SIZE + AES_KEY_SIZE;

    private final byte[] name;
    private final byte[] hmacKey;
    private final byte[] aesKey;

    public SessionTicketKey(byte[] name, byte[] hmacKey, byte[] aesKey) {
        this.name = name.clone();
        this.hmacKey = hmacKey.clone();
        this.aesKey = aesKey.clone();
    }

    public byte[] getName() {
        return name.clone();
    }

    public byte[] getHmacKey() {
        return hmacKey.clone();
    }

    public byte[] getAesKey() {
        return aesKey.clone();
    }
}
