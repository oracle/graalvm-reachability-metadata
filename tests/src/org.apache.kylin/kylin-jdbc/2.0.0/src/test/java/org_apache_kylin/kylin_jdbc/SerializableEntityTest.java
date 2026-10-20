/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.kylin.jdbc.shaded.org.apache.http.entity.SerializableEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableEntityTest {
    @Test
    void writesBufferedAndStreamingSerializableEntities() throws Exception {
        List<String> payload = Arrays.asList("cube", "sales");
        SerializableEntity buffered = new SerializableEntity((java.io.Serializable) payload, true);
        SerializableEntity streaming = new SerializableEntity((java.io.Serializable) payload, false);
        ByteArrayOutputStream bufferedBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream streamingBytes = new ByteArrayOutputStream();

        buffered.writeTo(bufferedBytes);
        streaming.writeTo(streamingBytes);

        assertThat(buffered.getContentLength()).isPositive();
        assertThat(bufferedBytes.toByteArray()).isEqualTo(streamingBytes.toByteArray());
    }
}
