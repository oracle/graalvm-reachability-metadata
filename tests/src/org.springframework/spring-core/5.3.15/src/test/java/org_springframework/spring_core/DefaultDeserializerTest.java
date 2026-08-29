/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.serializer.DefaultDeserializer;
import org.springframework.util.SerializationUtils;

/** Verifies deserialization from an input stream. */
public class DefaultDeserializerTest {
    @Test
    void deserializesObjectFromStream() throws Exception {
        List<String> original = Arrays.asList("spring", "core");
        ByteArrayInputStream input = new ByteArrayInputStream(SerializationUtils.serialize(original));

        Object result = new DefaultDeserializer(getClass().getClassLoader()).deserialize(input);

        assertThat(result).isEqualTo(original);
    }
}
