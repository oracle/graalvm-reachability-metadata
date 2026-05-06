/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents_core5.httpcore5;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.SerializableEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableEntityTest {
    @Test
    void writeToSerializesProvidedObject() throws Exception {
        final SerializableEntity entity = new SerializableEntity("native-image-safe payload", ContentType.TEXT_PLAIN);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        entity.writeTo(outputStream);

        assertThat(outputStream.size()).isGreaterThan(0);
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            assertThat(inputStream.readObject()).isEqualTo("native-image-safe payload");
        }
    }
}
