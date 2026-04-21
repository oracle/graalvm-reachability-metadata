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

import org.apache.commons.collections4.map.UnmodifiableMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnmodifiableMapTest {

    @Test
    void serializesAndDeserializesUnmodifiableMapDecorator() throws Exception {
        LinkedHashMap<String, Integer> delegate = new LinkedHashMap<>();
        delegate.put("alpha", 1);
        delegate.put("beta", 2);
        delegate.put("gamma", 3);

        Map<String, Integer> original = UnmodifiableMap.unmodifiableMap(delegate);

        assertThat(original)
                .isInstanceOf(UnmodifiableMap.class)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2)
                .containsEntry("gamma", 3);
        assertThatThrownBy(() -> original.put("delta", 4))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        Map<String, Integer> restored = deserializeMap(serialized);

        assertThat(restored)
                .isInstanceOf(UnmodifiableMap.class)
                .hasSize(3)
                .containsEntry("alpha", 1)
                .containsEntry("beta", 2)
                .containsEntry("gamma", 3);
        assertThat(restored.keySet()).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.values()).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> restored.put("delta", 4))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(restored::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.entrySet().iterator().next().setValue(10))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(Map<String, Integer> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> deserializeMap(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(UnmodifiableMap.class);
            return (Map<String, Integer>) restored;
        }
    }
}
