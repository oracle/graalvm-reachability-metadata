/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.multiset.HashMultiSet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractMapMultiSetTest {

    @Test
    void createsTypedArrayWhenTargetArrayIsTooSmall() {
        HashMultiSet<String> multiSet = new HashMultiSet<>();
        multiSet.add("alpha", 2);
        multiSet.add("beta", 1);

        String[] target = new String[1];
        String[] values = multiSet.toArray(target);

        assertThat(values)
                .isNotSameAs(target)
                .hasSize(3)
                .containsExactlyInAnyOrder("alpha", "alpha", "beta");
    }

    @Test
    void serializesAndDeserializesMultiSetEntriesWithCounts() throws Exception {
        HashMultiSet<String> original = new HashMultiSet<>();
        original.add("alpha", 2);
        original.add("beta", 1);
        original.add("gamma", 3);

        byte[] serialized = serialize(original);
        HashMultiSet<String> restored = deserializeHashMultiSet(serialized);

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

    private static byte[] serialize(HashMultiSet<String> multiSet) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(multiSet);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static HashMultiSet<String> deserializeHashMultiSet(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(HashMultiSet.class);
            return (HashMultiSet<String>) restored;
        }
    }
}
