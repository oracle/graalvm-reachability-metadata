/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.commons.collections.Bag;
import io.sundr.deps.org.apache.commons.collections.bag.HashBag;
import io.sundr.deps.org.apache.commons.collections.bag.UnmodifiableBag;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class UnmodifiableBagTest {

    @Test
    public void serializesAndDeserializesDecoratedBagEntriesWithCounts()
            throws IOException, ClassNotFoundException {
        HashBag decorated = new HashBag();
        decorated.add("alpha", 2);
        decorated.add("beta", 1);
        decorated.add("gamma", 3);
        Bag original = UnmodifiableBag.decorate(decorated);

        byte[] serialized = serialize(original);
        Bag restored = deserializeBag(serialized);

        assertThat(restored).isNotSameAs(original).isInstanceOf(UnmodifiableBag.class);
        assertThat(restored).hasSize(6);
        assertThat(restored.getCount("alpha")).isEqualTo(2);
        assertThat(restored.getCount("beta")).isEqualTo(1);
        assertThat(restored.getCount("gamma")).isEqualTo(3);
        assertThat(restored.uniqueSet())
                .containsExactlyInAnyOrder("alpha", "beta", "gamma");
        assertThatThrownBy(() -> restored.add("delta", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static byte[] serialize(Bag bag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(bag);
        }
        return bytes.toByteArray();
    }

    private static Bag deserializeBag(byte[] serialized) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(Bag.class);
            return (Bag) restored;
        }
    }
}
