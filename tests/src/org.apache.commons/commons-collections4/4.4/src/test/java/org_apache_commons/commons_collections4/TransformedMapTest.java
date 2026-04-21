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
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.map.TransformedMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TransformedMapTest {

    @Test
    void serializesAndDeserializesTransformersAndDecoratedMapState() throws Exception {
        Map<String, String> decorated = new LinkedHashMap<>();
        decorated.put("alpha", "one");

        TransformedMap<String, String> original = TransformedMap.transformedMap(
                decorated,
                new PrefixTransformer("key-"),
                new PrefixTransformer("value-"));
        original.put("beta", "two");

        Map<String, String> additionalEntries = new LinkedHashMap<>();
        additionalEntries.put("gamma", "three");
        original.putAll(additionalEntries);

        assertThat(original)
                .containsEntry("key-alpha", "value-one")
                .containsEntry("key-beta", "value-two")
                .containsEntry("key-gamma", "value-three");

        byte[] serialized = serialize(original);
        TransformedMap<String, String> restored = deserializeTransformedMap(serialized);

        assertThat(restored)
                .hasSize(3)
                .containsEntry("key-alpha", "value-one")
                .containsEntry("key-beta", "value-two")
                .containsEntry("key-gamma", "value-three");

        restored.put("delta", "four");
        assertThat(restored).containsEntry("key-delta", "value-four");

        Map.Entry<String, String> betaEntry = restored.entrySet().stream()
                .filter(entry -> entry.getKey().equals("key-beta"))
                .findFirst()
                .orElseThrow();
        assertThat(betaEntry.setValue("updated-two")).isEqualTo("value-two");
        assertThat(restored).containsEntry("key-beta", "value-updated-two");
    }

    private static byte[] serialize(TransformedMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static TransformedMap<String, String> deserializeTransformedMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(TransformedMap.class);
            return (TransformedMap<String, String>) restored;
        }
    }

    private static final class PrefixTransformer implements Transformer<String, String>, Serializable {

        private static final long serialVersionUID = 1L;

        private final String prefix;

        private PrefixTransformer(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String transform(String input) {
            return prefix + input;
        }
    }
}
