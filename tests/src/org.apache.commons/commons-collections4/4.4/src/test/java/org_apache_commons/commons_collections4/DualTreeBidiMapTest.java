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
import java.util.Comparator;

import org.apache.commons.collections4.bidimap.DualTreeBidiMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DualTreeBidiMapTest {

    @Test
    void serializesAndDeserializesEntriesWithCustomComparators() throws Exception {
        DualTreeBidiMap<String, String> original = new DualTreeBidiMap<>(
                new LengthThenNaturalComparator(),
                new ReverseAlphabeticalComparator());
        original.put("bbb", "alpha");
        original.put("a", "gamma");
        original.put("cc", "beta");

        byte[] serialized = serialize(original);
        DualTreeBidiMap<String, String> restored = deserializeDualTreeBidiMap(serialized);

        assertThat(restored)
                .hasSize(3)
                .containsEntry("a", "gamma")
                .containsEntry("cc", "beta")
                .containsEntry("bbb", "alpha");
        assertThat(restored.firstKey()).isEqualTo("a");
        assertThat(restored.nextKey("a")).isEqualTo("cc");
        assertThat(restored.lastKey()).isEqualTo("bbb");
        assertThat(restored.inverseBidiMap())
                .hasSize(3)
                .containsEntry("gamma", "a")
                .containsEntry("beta", "cc")
                .containsEntry("alpha", "bbb");
        assertThat(restored.inverseBidiMap().firstKey()).isEqualTo("gamma");

        restored.inverseBidiMap().put("delta", "dddd");

        assertThat(restored)
                .hasSize(4)
                .containsEntry("dddd", "delta");
        assertThat(restored.getKey("delta")).isEqualTo("dddd");
        assertThat(restored.lastKey()).isEqualTo("dddd");
    }

    private static byte[] serialize(DualTreeBidiMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static DualTreeBidiMap<String, String> deserializeDualTreeBidiMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(DualTreeBidiMap.class);
            return (DualTreeBidiMap<String, String>) restored;
        }
    }

    private static final class LengthThenNaturalComparator implements Comparator<String>, Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public int compare(String left, String right) {
            int lengthComparison = Integer.compare(left.length(), right.length());
            if (lengthComparison != 0) {
                return lengthComparison;
            }
            return left.compareTo(right);
        }
    }

    private static final class ReverseAlphabeticalComparator implements Comparator<String>, Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public int compare(String left, String right) {
            return right.compareTo(left);
        }
    }
}
