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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.SortedSet;
import java.util.TreeSet;
import org.immutables.value.internal.$guava$.base.$Supplier;
import org.junit.jupiter.api.Test;

class MultimapsCustomSortedSetMultimapTest {
    @Test
    void serializesAndDeserializesCustomSortedSetMultimap() throws Exception {
        final $SortedSetMultimap<String, String> source = $Multimaps.newSortedSetMultimap(
                new LinkedHashMap<>(),
                new SerializableReverseOrderStringTreeSetSupplier());
        source.put("letters", "a");
        source.put("letters", "c");
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
        final $SortedSetMultimap<String, String> restored = ($SortedSetMultimap<String, String>) restoredObject;
        assertThat(restored.get("letters")).containsExactly("c", "b", "a");
        assertThat(restored.get("numbers")).containsExactly("1");
        assertThat(restored.keySet()).containsExactly("letters", "numbers");
        assertThat(restored.valueComparator()).isNotNull();
        assertThat(restored.valueComparator().compare("c", "a")).isLessThan(0);

        restored.put("symbols", "!");
        restored.put("symbols", "#");
        assertThat(restored.get("symbols")).containsExactly("#", "!");
    }

    private static final class SerializableReverseOrderStringTreeSetSupplier
            implements $Supplier<SortedSet<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public SortedSet<String> get() {
            return new TreeSet<String>(Comparator.reverseOrder());
        }
    }
}
