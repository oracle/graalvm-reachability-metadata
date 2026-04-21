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
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.collections4.set.UnmodifiableSortedSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnmodifiableSortedSetTest {

    @Test
    void serializesAndDeserializesUnmodifiableSortedSetDecorator() throws Exception {
        TreeSet<String> delegate = new TreeSet<>();
        delegate.add("alpha");
        delegate.add("beta");
        delegate.add("delta");
        delegate.add("gamma");

        SortedSet<String> original = UnmodifiableSortedSet.unmodifiableSortedSet(delegate);

        assertThat(original)
                .isInstanceOf(UnmodifiableSortedSet.class)
                .containsExactly("alpha", "beta", "delta", "gamma");
        assertThat(original.comparator()).isNull();
        assertThat(original.first()).isEqualTo("alpha");
        assertThat(original.last()).isEqualTo("gamma");
        assertThatThrownBy(() -> original.add("epsilon"))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        SortedSet<String> restored = deserializeSortedSet(serialized);

        assertThat(restored)
                .isInstanceOf(UnmodifiableSortedSet.class)
                .containsExactly("alpha", "beta", "delta", "gamma");
        assertThat(restored.comparator()).isNull();
        assertThat(restored.first()).isEqualTo("alpha");
        assertThat(restored.last()).isEqualTo("gamma");

        SortedSet<String> headSet = restored.headSet("delta");
        assertThat(headSet)
                .isInstanceOf(UnmodifiableSortedSet.class)
                .containsExactly("alpha", "beta");

        SortedSet<String> subSet = restored.subSet("beta", "gamma");
        assertThat(subSet)
                .isInstanceOf(UnmodifiableSortedSet.class)
                .containsExactly("beta", "delta");

        SortedSet<String> tailSet = restored.tailSet("beta");
        assertThat(tailSet)
                .isInstanceOf(UnmodifiableSortedSet.class)
                .containsExactly("beta", "delta", "gamma");

        assertThatThrownBy(() -> headSet.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> subSet.clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> tailSet.removeIf(value -> value.startsWith("g")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.iterator().remove())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(SortedSet<String> set) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(set);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static SortedSet<String> deserializeSortedSet(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(UnmodifiableSortedSet.class);
            return (SortedSet<String>) restored;
        }
    }
}
