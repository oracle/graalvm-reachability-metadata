/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializationUtilsTest {
    @Test
    void serializesAndDeserializesAnIndependentMutableCollection() {
        ArrayList<String> source = new ArrayList<>(List.of("queue", "worker"));
        byte[] encoded = SerializationUtils.serialize(source);
        ArrayList<String> restored = SerializationUtils.deserialize(encoded);
        assertThat(restored).containsExactly("queue", "worker").isNotSameAs(source);
        restored.add("result");
        assertThat(source).containsExactly("queue", "worker");
    }
}
