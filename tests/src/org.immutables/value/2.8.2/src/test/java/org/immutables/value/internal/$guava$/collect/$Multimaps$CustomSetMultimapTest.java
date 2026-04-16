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
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import org.immutables.value.internal.$guava$.base.$Supplier;
import org.junit.jupiter.api.Test;

class $Multimaps$CustomSetMultimapTest {
    @Test
    void serializesAndDeserializesCustomSetMultimap() throws Exception {
        final $SetMultimap<String, String> source = $Multimaps.newSetMultimap(
                new LinkedHashMap<>(),
                new SerializableLinkedHashSetSupplier<>());
        source.put("letters", "a");
        source.put("letters", "b");
        source.put("letters", "a");
        source.put("numbers", "1");

        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteStream)) {
            outputStream.writeObject(source);
        }

        final Object restoredObject;
        try (ObjectInputStream inputStream = new ObjectInputStream(
                new ByteArrayInputStream(byteStream.toByteArray()))) {
            restoredObject = inputStream.readObject();
        }

        assertThat(restoredObject.getClass()).isEqualTo(source.getClass());

        @SuppressWarnings("unchecked")
        final $SetMultimap<String, String> restored = ($SetMultimap<String, String>) restoredObject;
        assertThat(restored.get("letters")).containsExactly("a", "b");
        assertThat(restored.get("numbers")).containsExactly("1");
        assertThat(restored.keySet()).containsExactly("letters", "numbers");

        restored.put("symbols", "!");
        assertThat(restored.get("symbols")).containsExactly("!");
    }

    private static final class SerializableLinkedHashSetSupplier<V>
            implements $Supplier<Set<V>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Set<V> get() {
            return new LinkedHashSet<>();
        }
    }
}
