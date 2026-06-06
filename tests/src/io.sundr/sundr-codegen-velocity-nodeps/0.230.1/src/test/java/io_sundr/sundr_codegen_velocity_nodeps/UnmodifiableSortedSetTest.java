/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.set.UnmodifiableSortedSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

public class UnmodifiableSortedSetTest {

    @Test
    public void serializesAndDeserializesDecoratedSortedSetStateAndUnmodifiableViews()
            throws IOException, ClassNotFoundException {
        SortedSet<String> decorated = new TreeSet<>();
        decorated.add("alpha");
        decorated.add("beta");
        decorated.add("gamma");

        SortedSet<String> original = UnmodifiableSortedSet.decorate(decorated);

        assertThat(original)
                .isInstanceOf(UnmodifiableSortedSet.class)
                .containsExactly("alpha", "beta", "gamma");
        assertThat(original.first()).isEqualTo("alpha");
        assertThat(original.last()).isEqualTo("gamma");
        assertThat(original.comparator()).isNull();
        assertThatThrownBy(() -> original.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        SortedSet<String> restored = deserializeSortedSet(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(UnmodifiableSortedSet.class);
        assertThat(restored).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.first()).isEqualTo("alpha");
        assertThat(restored.last()).isEqualTo("gamma");
        assertThat(restored.comparator()).isNull();

        assertThat(restored.subSet("alpha", "gamma"))
                .isInstanceOf(UnmodifiableSortedSet.class)
                .containsExactly("alpha", "beta");
        assertThat(restored.headSet("gamma"))
                .isInstanceOf(UnmodifiableSortedSet.class)
                .containsExactly("alpha", "beta");
        assertThat(restored.tailSet("beta"))
                .isInstanceOf(UnmodifiableSortedSet.class)
                .containsExactly("beta", "gamma");

        assertThatThrownBy(() -> restored.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.addAll(Collections.singleton("delta")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(restored::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        Iterator<String> iterator = restored.iterator();
        iterator.next();
        assertThatThrownBy(iterator::remove)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(SortedSet<String> set) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(set);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static SortedSet<String> deserializeSortedSet(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(SortedSet.class);
            return (SortedSet<String>) restored;
        }
    }
}
