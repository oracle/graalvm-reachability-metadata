/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpcore;

import org.apache.http.entity.SerializableEntity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableEntityTest {

    @Test
    void bufferizedEntitySerializesSourceObjectDuringConstruction() throws Exception {
        String payload = "buffered serializable content";
        byte[] expectedBytes = serialize(payload);

        SerializableEntity entity = new SerializableEntity(payload, true);

        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.isStreaming()).isFalse();
        assertThat(entity.getContentLength()).isEqualTo(expectedBytes.length);
        assertThat(readAllBytes(entity.getContent())).isEqualTo(expectedBytes);
    }

    @Test
    void streamingEntitySerializesSourceObjectWhenWritten() throws Exception {
        String payload = "streamed serializable content";
        SerializableEntity entity = new SerializableEntity(payload, false);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.isStreaming()).isTrue();
        assertThat(entity.getContentLength()).isEqualTo(-1);

        entity.writeTo(outputStream);

        assertThat(outputStream.toByteArray()).isEqualTo(serialize(payload));
    }

    private static byte[] readAllBytes(InputStream content) throws IOException {
        try (InputStream inputStream = content) {
            return inputStream.readAllBytes();
        }
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }
}
