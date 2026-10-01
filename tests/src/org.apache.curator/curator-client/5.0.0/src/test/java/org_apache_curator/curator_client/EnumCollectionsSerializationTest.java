/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.curator.shaded.com.google.common.collect.EnumBiMap;
import org.apache.curator.shaded.com.google.common.collect.EnumHashBiMap;
import org.apache.curator.shaded.com.google.common.collect.EnumMultiset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumCollectionsSerializationTest {
    private enum Key { FIRST, SECOND }

    private enum Value { ONE, TWO }

    @Test
    void preservesEnumCollectionsWhenRoundTripped() throws Exception {
        EnumBiMap<Key, Value> biMap = EnumBiMap.create(Key.class, Value.class);
        biMap.put(Key.FIRST, Value.ONE);
        EnumHashBiMap<Key, Value> hashBiMap = EnumHashBiMap.create(Key.class);
        hashBiMap.put(Key.SECOND, Value.TWO);
        EnumMultiset<Key> multiset = EnumMultiset.create(Key.class);
        multiset.add(Key.FIRST, 2);

        assertThat(roundTrip(biMap)).isEqualTo(biMap);
        assertThat(roundTrip(hashBiMap)).isEqualTo(hashBiMap);
        assertThat(roundTrip(multiset)).isEqualTo(multiset);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
