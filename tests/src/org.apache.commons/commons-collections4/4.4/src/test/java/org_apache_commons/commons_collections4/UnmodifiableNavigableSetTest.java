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
import java.util.NavigableSet;
import java.util.TreeSet;

import org.apache.commons.collections4.set.UnmodifiableNavigableSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnmodifiableNavigableSetTest {

    @Test
    void serializesAndDeserializesUnmodifiableNavigableSetDecorator() throws Exception {
        TreeSet<String> delegate = new TreeSet<>();
        delegate.add("alpha");
        delegate.add("beta");
        delegate.add("delta");
        delegate.add("gamma");

        NavigableSet<String> original = UnmodifiableNavigableSet.unmodifiableNavigableSet(delegate);

        assertThat(original)
                .isInstanceOf(UnmodifiableNavigableSet.class)
                .containsExactly("alpha", "beta", "delta", "gamma");
        assertThat(original.lower("delta")).isEqualTo("beta");
        assertThat(original.floor("delta")).isEqualTo("delta");
        assertThat(original.ceiling("delta")).isEqualTo("delta");
        assertThat(original.higher("delta")).isEqualTo("gamma");
        assertThatThrownBy(() -> original.add("epsilon"))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        NavigableSet<String> restored = deserializeNavigableSet(serialized);

        assertThat(restored)
                .isInstanceOf(UnmodifiableNavigableSet.class)
                .containsExactly("alpha", "beta", "delta", "gamma");
        assertThat(restored.comparator()).isNull();
        assertThat(restored.first()).isEqualTo("alpha");
        assertThat(restored.last()).isEqualTo("gamma");
        assertThat(restored.lower("delta")).isEqualTo("beta");
        assertThat(restored.floor("delta")).isEqualTo("delta");
        assertThat(restored.ceiling("delta")).isEqualTo("delta");
        assertThat(restored.higher("delta")).isEqualTo("gamma");

        NavigableSet<String> descendingSet = restored.descendingSet();
        assertThat(descendingSet)
                .isInstanceOf(UnmodifiableNavigableSet.class)
                .containsExactly("gamma", "delta", "beta", "alpha");

        NavigableSet<String> headSet = restored.headSet("delta", false);
        assertThat(headSet)
                .isInstanceOf(UnmodifiableNavigableSet.class)
                .containsExactly("alpha", "beta");

        NavigableSet<String> subSet = restored.subSet("beta", true, "gamma", false);
        assertThat(subSet)
                .isInstanceOf(UnmodifiableNavigableSet.class)
                .containsExactly("beta", "delta");

        NavigableSet<String> tailSet = restored.tailSet("beta", true);
        assertThat(tailSet)
                .isInstanceOf(UnmodifiableNavigableSet.class)
                .containsExactly("beta", "delta", "gamma");

        assertThatThrownBy(() -> descendingSet.add("epsilon"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> headSet.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> subSet.clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> tailSet.removeIf(value -> value.startsWith("g")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.iterator().remove())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.descendingIterator().remove())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(NavigableSet<String> set) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(set);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static NavigableSet<String> deserializeNavigableSet(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(UnmodifiableNavigableSet.class);
            return (NavigableSet<String>) restored;
        }
    }
}
