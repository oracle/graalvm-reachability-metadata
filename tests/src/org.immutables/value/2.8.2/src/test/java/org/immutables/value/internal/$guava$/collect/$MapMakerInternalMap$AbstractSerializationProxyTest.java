/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.immutables.value.internal.$guava$.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

class MapMakerInternalMapAbstractSerializationProxyTest {
    @Test
    void serializesAndDeserializesCustomMapEntries() throws Exception {
        final ConcurrentMap<String, Integer> source = new $MapMaker().maximumSize(10).makeMap();
        source.put("alpha", 1);
        source.put("beta", 2);

        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteStream)) {
            outputStream.writeObject(source);
        }

        final Map<?, ?> restored;
        try (ObjectInputStream inputStream = new ObjectInputStream(
                new ByteArrayInputStream(byteStream.toByteArray()))) {
            restored = (Map<?, ?>) inputStream.readObject();
        }

        assertThat(source.getClass().getName())
                .isEqualTo("org.immutables.value.internal.$guava$.collect.$MapMakerInternalMap");
        assertThat(restored.getClass()).isEqualTo(source.getClass());
        assertThat(restored).hasSize(2);
        assertThat(restored.get("alpha")).isEqualTo(1);
        assertThat(restored.get("beta")).isEqualTo(2);
    }
}
