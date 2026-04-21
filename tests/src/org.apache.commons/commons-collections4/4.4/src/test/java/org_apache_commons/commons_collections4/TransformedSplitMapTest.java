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

import org.apache.commons.collections4.SplitMapUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.splitmap.TransformedSplitMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TransformedSplitMapTest {

    @Test
    void serializesAndDeserializesTransformersAndDecoratedMapState() throws Exception {
        Map<String, String> decorated = new LinkedHashMap<>();

        TransformedSplitMap<String, String, String, String> original = TransformedSplitMap.transformingMap(
                decorated,
                new PrefixTransformer("key-"),
                new PrefixTransformer("value-"));
        original.put("alpha", "one");

        Map<String, String> additionalEntries = new LinkedHashMap<>();
        additionalEntries.put("beta", "two");
        original.putAll(additionalEntries);

        assertThat(SplitMapUtils.readableMap(original))
                .containsEntry("key-alpha", "value-one")
                .containsEntry("key-beta", "value-two");
        assertThat(decorated)
                .containsEntry("key-alpha", "value-one")
                .containsEntry("key-beta", "value-two");

        byte[] serialized = serialize(original);
        TransformedSplitMap<String, String, String, String> restored = deserializeTransformedSplitMap(serialized);

        assertThat(SplitMapUtils.readableMap(restored))
                .hasSize(2)
                .containsEntry("key-alpha", "value-one")
                .containsEntry("key-beta", "value-two");

        restored.put("gamma", "three");
        assertThat(SplitMapUtils.readableMap(restored)).containsEntry("key-gamma", "value-three");
    }

    private static byte[] serialize(TransformedSplitMap<String, String, String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static TransformedSplitMap<String, String, String, String> deserializeTransformedSplitMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(TransformedSplitMap.class);
            return (TransformedSplitMap<String, String, String, String>) restored;
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
