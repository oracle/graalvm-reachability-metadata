/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.Predicate;
import io.sundr.deps.org.apache.commons.collections.PredicateUtils;
import io.sundr.deps.org.apache.commons.collections.map.PredicatedMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class PredicatedMapTest {

    @Test
    public void serializesDecoratedMapStateAndPredicateConstraints()
            throws IOException, ClassNotFoundException {
        Map decorated = new LinkedHashMap();
        decorated.put("alpha", "one");
        decorated.put("beta", "two");

        Predicate keyPredicate = PredicateUtils.instanceofPredicate(String.class);
        Predicate valuePredicate = PredicateUtils.notNullPredicate();
        Map original = PredicatedMap.decorate(decorated, keyPredicate, valuePredicate);
        original.put("gamma", "three");

        byte[] serialized = serialize(original);
        Map restored = deserializeMap(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(PredicatedMap.class);
        assertThat(restored)
                .hasSize(3)
                .containsEntry("alpha", "one")
                .containsEntry("beta", "two")
                .containsEntry("gamma", "three");

        assertThat(restored.put("delta", "four")).isNull();
        assertThat(restored).containsEntry("delta", "four");
        assertThatThrownBy(() -> restored.put(Integer.valueOf(5), "five"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot add key");
        assertThatThrownBy(() -> restored.put("epsilon", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot add value");
    }

    private static byte[] serialize(Map map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static Map deserializeMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(Map.class);
            return (Map) restored;
        }
    }
}
