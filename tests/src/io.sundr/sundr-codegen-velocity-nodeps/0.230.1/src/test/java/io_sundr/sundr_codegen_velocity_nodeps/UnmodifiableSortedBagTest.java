/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.SortedBag;
import io.sundr.deps.org.apache.commons.collections.bag.TreeBag;
import io.sundr.deps.org.apache.commons.collections.bag.UnmodifiableSortedBag;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class UnmodifiableSortedBagTest {

    @Test
    public void serializesAndDeserializesDecoratedSortedBagEntriesWithCounts()
            throws IOException, ClassNotFoundException {
        TreeBag decorated = new TreeBag();
        decorated.add("gamma", 3);
        decorated.add("alpha", 2);
        decorated.add("beta", 1);
        SortedBag original = UnmodifiableSortedBag.decorate(decorated);

        byte[] serialized = serialize(original);
        SortedBag restored = deserializeSortedBag(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(UnmodifiableSortedBag.class);
        assertThat(restored).hasSize(6);
        assertThat(restored.getCount("alpha")).isEqualTo(2);
        assertThat(restored.getCount("beta")).isEqualTo(1);
        assertThat(restored.getCount("gamma")).isEqualTo(3);
        assertThat(restored.first()).isEqualTo("alpha");
        assertThat(restored.last()).isEqualTo("gamma");
        assertThat(restored.comparator()).isNull();
        assertThat(restored.uniqueSet()).containsExactly("alpha", "beta", "gamma");
        assertThatThrownBy(() -> restored.add("delta", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(SortedBag bag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(bag);
        }
        return bytes.toByteArray();
    }

    private static SortedBag deserializeSortedBag(byte[] serialized)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(SortedBag.class);
            return (SortedBag) restored;
        }
    }
}
