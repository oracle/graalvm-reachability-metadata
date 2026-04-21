/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.bag.HashBag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractMapBagTest {

    @Test
    void createsTypedArrayWhenTargetArrayIsTooSmall() {
        HashBag<String> bag = new HashBag<>();
        bag.add("alpha", 2);
        bag.add("beta", 1);

        String[] target = new String[1];
        String[] values = bag.toArray(target);

        assertThat(values)
                .isNotSameAs(target)
                .hasSize(3)
                .containsExactlyInAnyOrder("alpha", "alpha", "beta");
    }

    @Test
    void serializesAndDeserializesBagEntriesWithCounts() throws Exception {
        HashBag<String> original = new HashBag<>();
        original.add("alpha", 2);
        original.add("beta", 1);
        original.add("gamma", 3);

        byte[] serialized = serialize(original);
        HashBag<String> restored = deserializeHashBag(serialized);

        assertThat(restored)
                .hasSize(6);
        assertThat(restored.getCount("alpha")).isEqualTo(2);
        assertThat(restored.getCount("beta")).isEqualTo(1);
        assertThat(restored.getCount("gamma")).isEqualTo(3);
        assertThat(restored.uniqueSet())
                .containsExactlyInAnyOrder("alpha", "beta", "gamma");

        restored.add("beta", 2);

        assertThat(restored)
                .hasSize(8);
        assertThat(restored.getCount("beta")).isEqualTo(3);
    }

    private static byte[] serialize(HashBag<String> bag) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(bag);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static HashBag<String> deserializeHashBag(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(HashBag.class);
            return (HashBag<String>) restored;
        }
    }
}
