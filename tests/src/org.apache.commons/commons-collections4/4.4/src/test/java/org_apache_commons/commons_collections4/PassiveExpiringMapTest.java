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

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PassiveExpiringMapTest {

    @Test
    void serializesAndDeserializesMapStateAndExpirationPolicy() throws Exception {
        PassiveExpiringMap<String, String> original = new PassiveExpiringMap<>(new KeyBasedExpirationPolicy());
        original.put("immortal", "alpha");
        original.put("expired-entry", "beta");
        original.put("persistent", "gamma");

        byte[] serialized = serialize(original);
        PassiveExpiringMap<String, String> restored = deserializePassiveExpiringMap(serialized);

        assertThat(restored.get("expired-entry")).isNull();
        assertThat(restored)
                .containsEntry("immortal", "alpha")
                .containsEntry("persistent", "gamma")
                .doesNotContainKey("expired-entry")
                .hasSize(2);

        restored.put("restored-immortal", "delta");
        restored.put("expired-after-restore", "epsilon");

        assertThat(restored.get("restored-immortal")).isEqualTo("delta");
        assertThat(restored.get("expired-after-restore")).isNull();
        assertThat(restored)
                .containsEntry("restored-immortal", "delta")
                .doesNotContainKey("expired-after-restore")
                .hasSize(3);
    }

    private static byte[] serialize(PassiveExpiringMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static PassiveExpiringMap<String, String> deserializePassiveExpiringMap(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(PassiveExpiringMap.class);
            return (PassiveExpiringMap<String, String>) restored;
        }
    }

    private static final class KeyBasedExpirationPolicy implements PassiveExpiringMap.ExpirationPolicy<String, String> {

        private static final long serialVersionUID = 1L;

        @Override
        public long expirationTime(String key, String value) {
            if (key.startsWith("expired")) {
                return 0L;
            }
            return -1L;
        }
    }
}
