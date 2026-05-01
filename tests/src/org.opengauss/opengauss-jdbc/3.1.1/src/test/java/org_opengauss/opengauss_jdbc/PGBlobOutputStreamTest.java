/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.PGBlobOutputStream;
import org.postgresql.core.types.PGBlob;

import static org.assertj.core.api.Assertions.assertThat;

public class PGBlobOutputStreamTest {
    @Test
    void writesThroughPrimitiveByteBufferCreatedByConstructor() throws Exception {
        PGBlob blob = new PGBlob();
        PGBlobOutputStream output = new PGBlobOutputStream(blob, 2);

        output.write(1);
        output.write(2);

        assertThat(blob.length()).isZero();

        output.write(3);

        assertThat(blob.length()).isEqualTo(2L);
        assertThat(blob.getBytes(1, 2)).containsExactly((byte) 1, (byte) 2);

        output.write(new byte[]{4, 5, 6, 7}, 0, 4);

        assertThat(blob.length()).isEqualTo(7L);
        assertThat(blob.getBytes(1, 7)).containsExactly(
                (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6, (byte) 7);
    }
}
