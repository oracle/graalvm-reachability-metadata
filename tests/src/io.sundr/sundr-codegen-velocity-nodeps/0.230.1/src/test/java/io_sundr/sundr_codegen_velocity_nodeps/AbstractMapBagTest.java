/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.bag.HashBag;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class AbstractMapBagTest {

    @Test
    public void createsTypedArrayWhenTargetArrayIsTooSmall() {
        HashBag bag = new HashBag();
        bag.add("alpha", 2);
        bag.add("beta", 1);

        String[] target = new String[1];
        String[] values = (String[]) bag.toArray(target);

        assertThat(values)
                .isNotSameAs(target)
                .hasSize(3)
                .containsExactlyInAnyOrder("alpha", "alpha", "beta");
    }

    @Test
    public void serializesAndDeserializesBagEntriesWithCounts() throws IOException, ClassNotFoundException {
        HashBag original = new HashBag();
        original.add("alpha", 2);
        original.add("beta", 1);
        original.add("gamma", 3);

        byte[] serialized = serialize(original);
        HashBag restored = deserializeHashBag(serialized);

        assertThat(restored).hasSize(6);
        assertThat(restored.getCount("alpha")).isEqualTo(2);
        assertThat(restored.getCount("beta")).isEqualTo(1);
        assertThat(restored.getCount("gamma")).isEqualTo(3);
        assertThat(restored.uniqueSet())
                .containsExactlyInAnyOrder("alpha", "beta", "gamma");

        restored.add("beta", 2);

        assertThat(restored).hasSize(8);
        assertThat(restored.getCount("beta")).isEqualTo(3);
    }

    private static byte[] serialize(HashBag bag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(bag);
        }
        return bytes.toByteArray();
    }

    private static HashBag deserializeHashBag(byte[] serialized) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(HashBag.class);
            return (HashBag) restored;
        }
    }
}
