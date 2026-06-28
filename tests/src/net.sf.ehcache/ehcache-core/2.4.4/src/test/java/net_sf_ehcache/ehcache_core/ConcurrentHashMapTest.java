/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import net.sf.ehcache.store.chm.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

public class ConcurrentHashMapTest {
    @Test
    void serializesAndDeserializesMappings() throws Exception {
        ConcurrentHashMap<String, Integer> original = new ConcurrentHashMap<String, Integer>(4, 0.75f, 2);
        original.put("one", 1);
        original.put("two", 2);

        byte[] serialized = serialize(original);
        ConcurrentHashMap<String, Integer> restored = deserialize(serialized);

        assertThat(restored).hasSize(2);
        assertThat(restored.get("one")).isEqualTo(1);
        assertThat(restored.get("two")).isEqualTo(2);

        original.put("three", 3);
        assertThat(restored).doesNotContainKey("three");
    }

    private static byte[] serialize(ConcurrentHashMap<String, Integer> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Integer> deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (ConcurrentHashMap<String, Integer>) input.readObject();
        }
    }
}
