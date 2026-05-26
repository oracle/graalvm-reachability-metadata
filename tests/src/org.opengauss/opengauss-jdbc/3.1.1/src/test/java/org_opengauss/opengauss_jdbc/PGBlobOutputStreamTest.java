/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.core.types.PGBlob;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;

import static org.assertj.core.api.Assertions.assertThat;

public class PGBlobOutputStreamTest {
    @Test
    void writesBlobContentsThroughBinaryStream() throws Exception {
        Blob blob = new PGBlob();
        byte[] expected = "openGauss blob data".getBytes(StandardCharsets.UTF_8);

        try (OutputStream outputStream = blob.setBinaryStream(1)) {
            outputStream.write(expected);
        }

        assertThat(blob.length()).isEqualTo(expected.length);
        assertThat(blob.getBytes(1, expected.length)).isEqualTo(expected);
    }
}
