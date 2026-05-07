/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_transport_zerodep;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.ContentType;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.io.entity.SerializableEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableEntityTest {
    private static final byte STREAM_MAGIC_HIGH = (byte) 0xAC;
    private static final byte STREAM_MAGIC_LOW = (byte) 0xED;
    private static final byte STREAM_VERSION_HIGH = 0x00;
    private static final byte STREAM_VERSION_LOW = 0x05;

    @Test
    void writesSerializableObjectToOutputStream() throws Exception {
        final String payload = "docker-java-serializable-entity";
        final SerializableEntity entity = new SerializableEntity(payload, ContentType.APPLICATION_OCTET_STREAM);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        entity.writeTo(outputStream);

        final byte[] serialized = outputStream.toByteArray();
        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.isStreaming()).isFalse();
        assertThat(entity.getContentLength()).isEqualTo(-1);
        assertThat(serialized).startsWith(STREAM_MAGIC_HIGH, STREAM_MAGIC_LOW, STREAM_VERSION_HIGH, STREAM_VERSION_LOW);
        assertThat(new String(serialized, StandardCharsets.ISO_8859_1)).contains(payload);
    }

    @Test
    void contentStreamContainsSerializedObject() throws Exception {
        final String payload = "docker-java-content-stream";
        final SerializableEntity entity = new SerializableEntity(payload, ContentType.APPLICATION_OCTET_STREAM);

        try (InputStream content = entity.getContent()) {
            final byte[] serialized = content.readAllBytes();

            assertThat(serialized).startsWith(STREAM_MAGIC_HIGH, STREAM_MAGIC_LOW, STREAM_VERSION_HIGH, STREAM_VERSION_LOW);
            assertThat(new String(serialized, StandardCharsets.ISO_8859_1)).contains(payload);
        }
    }
}
