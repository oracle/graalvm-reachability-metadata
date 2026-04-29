/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpcore;

import org.apache.http.entity.SerializableEntity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableEntityTest {

    @Test
    public void bufferizedEntitySerializesPayloadWhenCreated() throws Exception {
        String payload = "buffered payload";

        SerializableEntity entity = new SerializableEntity(payload, true);

        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.isStreaming()).isFalse();
        assertThat(entity.getContentLength()).isGreaterThan(0);

        byte[] firstRead = readAllBytes(entity.getContent());
        byte[] secondRead = readAllBytes(entity.getContent());

        assertThat(firstRead).hasSize((int) entity.getContentLength());
        assertThat(secondRead).isEqualTo(firstRead);
        assertThat(deserialize(firstRead)).isEqualTo(payload);
    }

    @Test
    public void nonBufferizedEntityWritesSerializedPayloadToOutputStream() throws Exception {
        String payload = "streaming payload";
        SerializableEntity entity = new SerializableEntity(payload, false);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.isStreaming()).isTrue();
        assertThat(entity.getContentLength()).isEqualTo(-1);

        entity.writeTo(outputStream);

        assertThat(outputStream.size()).isGreaterThan(0);
        assertThat(deserialize(outputStream.toByteArray())).isEqualTo(payload);
    }

    private static byte[] readAllBytes(InputStream content) throws IOException {
        try (InputStream inputStream = content; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return inputStream.readObject();
        }
    }
}
