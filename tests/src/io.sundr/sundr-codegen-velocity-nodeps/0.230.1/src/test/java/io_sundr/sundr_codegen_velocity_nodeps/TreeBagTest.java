/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.bag.TreeBag;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class TreeBagTest {

    @Test
    public void serializesAndDeserializesSortedBagEntriesWithCounts()
            throws IOException, ClassNotFoundException {
        TreeBag original = new TreeBag();
        original.add("gamma", 3);
        original.add("alpha", 2);
        original.add("beta", 1);

        byte[] serialized = serialize(original);
        TreeBag restored = deserialize(serialized);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(6);
        assertThat(restored.getCount("alpha")).isEqualTo(2);
        assertThat(restored.getCount("beta")).isEqualTo(1);
        assertThat(restored.getCount("gamma")).isEqualTo(3);
        assertThat(restored.first()).isEqualTo("alpha");
        assertThat(restored.last()).isEqualTo("gamma");
        assertThat(restored.comparator()).isNull();
    }

    private static byte[] serialize(TreeBag bag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(bag);
        }
        return bytes.toByteArray();
    }

    private static TreeBag deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream inputStream = new ObjectInputStream(bytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(TreeBag.class);
            return (TreeBag) restored;
        }
    }
}
