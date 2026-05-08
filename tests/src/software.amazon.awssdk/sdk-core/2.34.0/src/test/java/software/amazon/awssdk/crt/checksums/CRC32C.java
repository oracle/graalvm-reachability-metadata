/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software.amazon.awssdk.crt.checksums;

import java.util.zip.Checksum;

public class CRC32C implements Checksum, Cloneable {
    private long value;

    @Override
    public void update(int b) {
        value = (value * 31 + (b & 0xFF)) & 0xFFFFFFFFL;
    }

    @Override
    public void update(byte[] b, int off, int len) {
        for (int index = off; index < off + len; index++) {
            update(b[index]);
        }
    }

    @Override
    public long getValue() {
        return value;
    }

    @Override
    public void reset() {
        value = 0;
    }

    @Override
    public CRC32C clone() {
        try {
            return (CRC32C) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
