/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package oracle.sql;

import java.util.Arrays;

public final class TIMESTAMPTZ {
    private final byte[] bytes;

    public TIMESTAMPTZ(byte[] bytes) {
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    public byte[] toBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
