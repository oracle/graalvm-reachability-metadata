/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.util.SerializationUtils;

/** Verifies Spring's byte-array serialization round trip. */
public class SerializationUtilsTest {
    @Test
    void serializesAndDeserializesValue() {
        List<String> original = new ArrayList<>();
        original.add("spring");
        original.add("core");

        byte[] serialized = SerializationUtils.serialize(original);

        assertThat(SerializationUtils.deserialize(serialized)).isEqualTo(original);
    }
}
