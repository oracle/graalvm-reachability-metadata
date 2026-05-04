/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software.amazon.awssdk.crt.checksums;

import java.util.zip.Checksum;

public final class CRC64NVME implements Checksum {
    private long value;

    public CRC64NVME() {
    }

    @Override
    public void update(int b) {
        value += b & 0xFFL;
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
        value = 0L;
    }
}
