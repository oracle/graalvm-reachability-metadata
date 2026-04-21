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

import org.apache.commons.collections4.SortedBag;
import org.apache.commons.collections4.bag.TreeBag;
import org.apache.commons.collections4.bag.UnmodifiableSortedBag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnmodifiableSortedBagTest {

    @Test
    void serializesAndDeserializesUnmodifiableSortedBagDecorator() throws Exception {
        TreeBag<String> delegate = new TreeBag<>();
        delegate.add("gamma", 2);
        delegate.add("alpha", 1);
        delegate.add("beta", 3);

        SortedBag<String> original = UnmodifiableSortedBag.unmodifiableSortedBag(delegate);

        assertThat(original).isInstanceOf(UnmodifiableSortedBag.class);
        assertThat(original.comparator()).isNull();
        assertThat(original.first()).isEqualTo("alpha");
        assertThat(original.last()).isEqualTo("gamma");
        assertThatThrownBy(() -> original.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        SortedBag<String> restored = deserializeSortedBag(serialized);

        assertThat(restored)
                .isInstanceOf(UnmodifiableSortedBag.class)
                .hasSize(6)
                .containsExactly("alpha", "beta", "beta", "beta", "gamma", "gamma");
        assertThat(restored.uniqueSet()).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.getCount("beta")).isEqualTo(3);
        assertThat(restored.comparator()).isNull();
        assertThat(restored.first()).isEqualTo("alpha");
        assertThat(restored.last()).isEqualTo("gamma");
        assertThatThrownBy(() -> restored.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove("beta", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(SortedBag<String> bag) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(bag);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static SortedBag<String> deserializeSortedBag(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(UnmodifiableSortedBag.class);
            return (SortedBag<String>) restored;
        }
    }
}
