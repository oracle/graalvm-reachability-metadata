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

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.collections4.bag.UnmodifiableBag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnmodifiableBagTest {

    @Test
    void serializesAndDeserializesUnmodifiableBagDecorator() throws Exception {
        HashBag<String> delegate = new HashBag<>();
        delegate.add("alpha", 2);
        delegate.add("beta", 1);
        delegate.add("gamma", 3);

        Bag<String> original = UnmodifiableBag.unmodifiableBag(delegate);

        assertThat(original).isInstanceOf(UnmodifiableBag.class);
        assertThatThrownBy(() -> original.add("delta"))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] serialized = serialize(original);
        Bag<String> restored = deserializeBag(serialized);

        assertThat(restored)
                .isInstanceOf(UnmodifiableBag.class)
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
        assertThatThrownBy(() -> restored.remove("alpha"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(Bag<String> bag) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(bag);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Bag<String> deserializeBag(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(UnmodifiableBag.class);
            return (Bag<String>) restored;
        }
    }
}
