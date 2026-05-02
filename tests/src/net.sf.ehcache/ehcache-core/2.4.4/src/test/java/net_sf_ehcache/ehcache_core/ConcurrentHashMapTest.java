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
    void serializesAndRestoresEntries() throws Exception {
        ConcurrentHashMap<String, String> map = new ConcurrentHashMap<String, String>();
        map.put("primary-key", "primary-value");
        map.put("secondary-key", "secondary-value");

        Object restoredObject = deserialize(serialize(map));

        assertThat(restoredObject).isInstanceOf(ConcurrentHashMap.class);
        ConcurrentHashMap<?, ?> restored = (ConcurrentHashMap<?, ?>) restoredObject;
        assertThat(restored).hasSize(2);
        assertThat(restored.get("primary-key")).isEqualTo("primary-value");
        assertThat(restored.get("secondary-key")).isEqualTo("secondary-value");
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return inputStream.readObject();
        }
    }
}
