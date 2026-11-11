/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import com.mysql.cj.protocol.x.CompressionAlgorithm;
import com.mysql.cj.protocol.x.CompressionMode;
import com.mysql.cj.protocol.x.CompressorStreamsFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class CompressionAlgorithmTest {
    @Test
    void defaultDeflateAlgorithmCompressesAndInflatesPayload() throws Exception {
        CompressionAlgorithm algorithm = CompressionAlgorithm.getDefaultInstances().get("deflate_stream");
        assertThat(algorithm.getCompressionMode()).isEqualTo(CompressionMode.STREAM);
        CompressorStreamsFactory factory = new CompressorStreamsFactory(algorithm);
        byte[] payload = "Connector compression round trip".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (OutputStream compressor = factory.getOutputStreamInstance(encoded)) {
            compressor.write(payload);
            compressor.flush();
            try (InputStream inflater = factory.getInputStreamInstance(new ByteArrayInputStream(encoded.toByteArray()))) {
                assertThat(inflater.readNBytes(payload.length)).isEqualTo(payload);
            }
        }
    }
}
