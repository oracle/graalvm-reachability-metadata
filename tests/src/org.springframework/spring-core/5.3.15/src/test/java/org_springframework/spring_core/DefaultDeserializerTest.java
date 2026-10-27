/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.core.serializer.DefaultDeserializer;
import org.springframework.core.serializer.DefaultSerializer;

/** Verifies deserialization from an input stream. */
public class DefaultDeserializerTest {
    @Test
    void readsSerializedObject() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new DefaultSerializer().serialize(Arrays.asList("spring", "core"), output);

        Object restored = new DefaultDeserializer().deserialize(
                new ByteArrayInputStream(output.toByteArray()));

        assertThat(restored).isEqualTo(Arrays.asList("spring", "core"));
    }
}
