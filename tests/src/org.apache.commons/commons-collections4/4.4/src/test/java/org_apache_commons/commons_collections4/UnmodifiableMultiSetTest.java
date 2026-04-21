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

import org.apache.commons.collections4.MultiSet;
import org.apache.commons.collections4.multiset.HashMultiSet;
import org.apache.commons.collections4.multiset.UnmodifiableMultiSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnmodifiableMultiSetTest {

    @Test
    void serializesAndDeserializesUnmodifiableMultiSetDecorator() throws Exception {
        HashMultiSet<String> delegate = new HashMultiSet<>();
        delegate.add("alpha", 2);
        delegate.add("beta", 1);
        delegate.add("gamma", 3);

        MultiSet<String> original = UnmodifiableMultiSet.unmodifiableMultiSet(delegate);

        assertThat(original)
                .isInstanceOf(UnmodifiableMultiSet.class)
                .hasSize(6)
                .containsExactlyInAnyOrder(
                        "alpha", "alpha",
                        "beta",
                        "gamma", "gamma", "gamma");
        assertThat(original.getCount("alpha")).isEqualTo(2);
        assertThat(original.getCount("beta")).isEqualTo(1);
        assertThat(original.getCount("gamma")).isEqualTo(3);
        assertThat(original.uniqueSet()).containsExactlyInAnyOrder("alpha", "beta", "gamma");
        assertThatThrownBy(() -> original.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        MultiSet<String> restored = deserializeMultiSet(serialized);

        assertThat(restored)
                .isInstanceOf(UnmodifiableMultiSet.class)
                .hasSize(6)
                .containsExactlyInAnyOrder(
                        "alpha", "alpha",
                        "beta",
                        "gamma", "gamma", "gamma");
        assertThat(restored.getCount("alpha")).isEqualTo(2);
        assertThat(restored.getCount("beta")).isEqualTo(1);
        assertThat(restored.getCount("gamma")).isEqualTo(3);
        assertThat(restored.uniqueSet()).containsExactlyInAnyOrder("alpha", "beta", "gamma");
        assertThatThrownBy(() -> restored.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.remove("alpha", 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.uniqueSet().remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(MultiSet<String> multiSet) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(multiSet);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static MultiSet<String> deserializeMultiSet(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(UnmodifiableMultiSet.class);
            return (MultiSet<String>) restored;
        }
    }
}
