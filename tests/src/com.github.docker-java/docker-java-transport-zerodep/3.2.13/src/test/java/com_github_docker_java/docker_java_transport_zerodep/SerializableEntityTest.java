/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_transport_zerodep;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.ContentType;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.io.entity.SerializableEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableEntityTest {
    @Test
    void writeToSerializesObjectContent() throws Exception {
        String content = "serializable entity payload";
        SerializableEntity entity = new SerializableEntity(content, ContentType.TEXT_PLAIN);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        entity.writeTo(outputStream);

        ByteArrayInputStream input = new ByteArrayInputStream(outputStream.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(input)) {
            Object deserializedContent = inputStream.readObject();
            assertThat(deserializedContent).isEqualTo(content);
        }
        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.isStreaming()).isFalse();
        assertThat(entity.getContentLength()).isEqualTo(-1L);
    }
}
