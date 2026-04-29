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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SerializableEntityTest {

    @Test
    void bufferizedEntitySerializesContentWhenCreated() throws Exception {
        ArrayList<String> payload = new ArrayList<>(List.of("alpha", "beta"));

        SerializableEntity entity = new SerializableEntity(payload, true);
        byte[] expectedBytes = serialize(payload);

        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.isStreaming()).isFalse();
        assertThat(entity.getContentLength()).isEqualTo(expectedBytes.length);
        assertThat(readAllBytes(entity.getContent())).isEqualTo(expectedBytes);
    }

    @Test
    void streamingEntityWritesSerializedObjectToOutputStream() throws Exception {
        ArrayList<String> payload = new ArrayList<>(List.of("gamma", "delta"));

        SerializableEntity entity = new SerializableEntity(payload);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThat(entity.isStreaming()).isTrue();

        entity.writeTo(out);

        assertThat(out.toByteArray()).isEqualTo(serialize(payload));
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
