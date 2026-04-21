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
import java.util.List;

import org.apache.commons.collections4.bag.CollectionBag;
import org.apache.commons.collections4.bag.HashBag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionBagTest {

    @Test
    void serializesAndDeserializesDecoratedBagState() throws Exception {
        CollectionBag<String> original = new CollectionBag<>(new HashBag<>());
        original.add("alpha", 2);
        original.add("beta", 1);
        original.add("gamma", 3);

        byte[] serialized = serialize(original);
        CollectionBag<String> restored = deserializeCollectionBag(serialized);

        assertThat(restored)
                .isInstanceOf(CollectionBag.class)
                .hasSize(6);
        assertThat(restored.getCount("alpha")).isEqualTo(2);
        assertThat(restored.getCount("beta")).isEqualTo(1);
        assertThat(restored.getCount("gamma")).isEqualTo(3);
        assertThat(restored.uniqueSet())
                .containsExactlyInAnyOrder("alpha", "beta", "gamma");

        restored.remove("gamma");
        restored.addAll(List.of("beta", "delta"));

        assertThat(restored.getCount("gamma")).isEqualTo(2);
        assertThat(restored.getCount("beta")).isEqualTo(2);
        assertThat(restored.getCount("delta")).isEqualTo(1);
        assertThat(restored)
                .hasSize(7)
                .containsExactlyInAnyOrder(
                        "alpha", "alpha",
                        "beta", "beta",
                        "gamma", "gamma",
                        "delta");
    }

    private static byte[] serialize(CollectionBag<String> bag) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(bag);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static CollectionBag<String> deserializeCollectionBag(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(CollectionBag.class);
            return (CollectionBag<String>) restored;
        }
    }
}
