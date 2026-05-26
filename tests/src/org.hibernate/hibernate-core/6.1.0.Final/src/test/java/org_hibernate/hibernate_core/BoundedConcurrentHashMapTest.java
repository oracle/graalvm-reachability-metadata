/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.collections.BoundedConcurrentHashMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class BoundedConcurrentHashMapTest {

    @Test
    public void serializesEntriesAndEndOfStreamMarker() throws Exception {
        BoundedConcurrentHashMap<String, String> map = new BoundedConcurrentHashMap<>(4, 1);
        map.put("hibernate", "orm");
        map.put("cache", "bounded");

        byte[] serialized = serialize(map);

        assertThat(serialized).isNotEmpty();
    }

    @Test
    public void deserializesEmptyMap() throws Exception {
        BoundedConcurrentHashMap<String, String> map = new BoundedConcurrentHashMap<>(4, 1);

        BoundedConcurrentHashMap<?, ?> deserialized = deserialize(serialize(map));

        assertThat(deserialized).isEmpty();
    }

    private static byte[] serialize(BoundedConcurrentHashMap<?, ?> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static BoundedConcurrentHashMap<?, ?> deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object deserialized = input.readObject();
            assertThat(deserialized).isInstanceOf(BoundedConcurrentHashMap.class);
            return (BoundedConcurrentHashMap<?, ?>) deserialized;
        }
    }

}
