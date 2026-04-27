/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents_core5.httpcore5;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.SerializableEntity;
import org.junit.jupiter.api.Test;

public class SerializableEntityTest {

    @Test
    void writeToSerializesPayloadToOutputStream() throws Exception {
        final String payload = "httpcore serializable entity payload";
        final SerializableEntity entity = new SerializableEntity(payload, ContentType.APPLICATION_OCTET_STREAM);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        entity.writeTo(outputStream);

        assertThat(outputStream.size()).isPositive();
        assertThat(deserialize(outputStream.toByteArray())).isEqualTo(payload);
    }

    @Test
    void getContentReturnsSerializedPayloadStream() throws Exception {
        final String payload = "repeatable entity content";
        final SerializableEntity entity = new SerializableEntity(payload, ContentType.TEXT_PLAIN);

        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.isStreaming()).isFalse();
        assertThat(entity.getContentLength()).isEqualTo(-1);
        assertThat(deserialize(entity.getContent().readAllBytes())).isEqualTo(payload);
    }

    private static Serializable deserialize(final byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Serializable) objectInputStream.readObject();
        }
    }
}
