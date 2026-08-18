/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_transport_zerodep;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.ContentType;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.io.entity.SerializableEntity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableEntityTest {
    @Test
    void writesSerializedObjectToOutputStream() throws Exception {
        SerializableEntity entity = new SerializableEntity("docker-java", ContentType.TEXT_PLAIN);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        entity.writeTo(outputStream);

        byte[] serializedBytes = outputStream.toByteArray();
        assertThat(serializedBytes).hasSizeGreaterThan("docker-java".length());
        assertThat(serializedBytes[0]).isEqualTo((byte) 0xac);
        assertThat(serializedBytes[1]).isEqualTo((byte) 0xed);
        assertThat(serializedBytes[2]).isEqualTo((byte) 0x00);
        assertThat(serializedBytes[3]).isEqualTo((byte) 0x05);
    }
}
