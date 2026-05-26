/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.collections.ConcurrentReferenceHashMap;
import org.hibernate.internal.util.collections.ConcurrentReferenceHashMap.ReferenceType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentReferenceHashMapTest {

    @Test
    public void serializesEntriesAndRestoresThem() throws Exception {
        ConcurrentReferenceHashMap<String, String> map = new ConcurrentReferenceHashMap<>(
                8,
                ReferenceType.STRONG,
                ReferenceType.STRONG
        );
        map.put("hibernate", "orm");
        map.put("metadata", "reachability");

        ConcurrentReferenceHashMap<?, ?> deserialized = deserialize(serialize(map));

        assertThat(deserialized.get("hibernate")).isEqualTo("orm");
        assertThat(deserialized.get("metadata")).isEqualTo("reachability");
        assertThat(deserialized).hasSize(2);
    }

    private static byte[] serialize(ConcurrentReferenceHashMap<?, ?> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static ConcurrentReferenceHashMap<?, ?> deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object deserialized = input.readObject();
            assertThat(deserialized).isInstanceOf(ConcurrentReferenceHashMap.class);
            return (ConcurrentReferenceHashMap<?, ?>) deserialized;
        }
    }

}
