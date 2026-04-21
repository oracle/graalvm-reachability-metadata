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

import org.apache.commons.collections4.bag.CollectionSortedBag;
import org.apache.commons.collections4.bag.TreeBag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionSortedBagTest {

    @Test
    void serializesAndDeserializesSortedBagDecoratorState() throws Exception {
        CollectionSortedBag<String> original = new CollectionSortedBag<>(new TreeBag<>());
        original.add("gamma", 2);
        original.add("alpha", 1);
        original.add("beta", 3);

        byte[] serialized = serialize(original);
        CollectionSortedBag<String> restored = deserializeCollectionSortedBag(serialized);

        assertThat(restored)
                .hasSize(6)
                .containsExactly("alpha", "beta", "beta", "beta", "gamma", "gamma");
        assertThat(restored.uniqueSet()).containsExactly("alpha", "beta", "gamma");
        assertThat(restored.getCount("beta")).isEqualTo(3);
        assertThat(restored.comparator()).isNull();
        assertThat(restored.first()).isEqualTo("alpha");
        assertThat(restored.last()).isEqualTo("gamma");

        restored.remove("beta");
        restored.add("delta", 2);

        assertThat(restored.getCount("beta")).isEqualTo(2);
        assertThat(restored)
                .hasSize(7)
                .containsExactly("alpha", "beta", "beta", "delta", "delta", "gamma", "gamma");
    }

    private static byte[] serialize(CollectionSortedBag<String> bag) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(bag);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static CollectionSortedBag<String> deserializeCollectionSortedBag(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(CollectionSortedBag.class);
            return (CollectionSortedBag<String>) restored;
        }
    }
}
