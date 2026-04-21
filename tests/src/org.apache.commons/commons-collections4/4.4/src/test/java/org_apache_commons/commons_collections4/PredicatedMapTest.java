/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.collections4.functors.NotNullPredicate;
import org.apache.commons.collections4.map.PredicatedMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PredicatedMapTest {

    @Test
    void serializesAndDeserializesPredicatesAndDecoratedMapState() throws Exception {
        PredicatedMap<String, String> original = PredicatedMap.predicatedMap(
                new LinkedHashMap<>(),
                NotNullPredicate.notNullPredicate(),
                NotNullPredicate.notNullPredicate());
        original.put("alpha", "one");
        original.put("beta", "two");

        assertThatThrownBy(() -> original.put(null, "rejected"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot add key");
        assertThatThrownBy(() -> original.put("gamma", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot add value");

        byte[] serialized = serialize(original);
        PredicatedMap<String, String> restored = deserializePredicatedMap(serialized);

        assertThat(restored)
                .hasSize(2)
                .containsEntry("alpha", "one")
                .containsEntry("beta", "two");

        restored.put("gamma", "three");
        assertThat(restored).containsEntry("gamma", "three");

        Map.Entry<String, String> betaEntry = restored.entrySet().stream()
                .filter(entry -> entry.getKey().equals("beta"))
                .findFirst()
                .orElseThrow();
        assertThat(betaEntry.setValue("updated-two")).isEqualTo("two");
        assertThat(restored).containsEntry("beta", "updated-two");

        assertThatThrownBy(() -> restored.put(null, "still-rejected"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot add key");
        assertThatThrownBy(() -> restored.put("delta", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot add value");
        assertThatThrownBy(() -> betaEntry.setValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot set value");
    }

    private static byte[] serialize(PredicatedMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static PredicatedMap<String, String> deserializePredicatedMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(PredicatedMap.class);
            return (PredicatedMap<String, String>) restored;
        }
    }
}
