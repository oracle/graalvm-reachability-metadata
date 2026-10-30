/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_sgroschupf.zkclient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.I0Itec.zkclient.serialize.SerializableSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

@Timeout(120)
public class SerializableSerializerTest {
    @Test
    void roundTripsNestedApplicationData() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("workers", new ArrayList<>(List.of("primary", "backup")));
        settings.put("retries", Integer.valueOf(3));
        settings.put("enabled", Boolean.TRUE);
        SerializableSerializer serializer = new SerializableSerializer();

        byte[] encoded = serializer.serialize(settings);
        Object decoded = serializer.deserialize(encoded);

        assertEquals(settings, decoded);
        assertNotSame(settings, decoded);
        Map<?, ?> restored = assertInstanceOf(Map.class, decoded);
        assertNotSame(settings.get("workers"), restored.get("workers"));
    }

    @Test
    void roundTripsBinaryDataAndNull() {
        SerializableSerializer serializer = new SerializableSerializer();
        byte[] payload = {0, 1, 42, -1};

        byte[] restored = assertInstanceOf(byte[].class, serializer.deserialize(serializer.serialize(payload)));

        assertArrayEquals(payload, restored);
        assertNotSame(payload, restored);
        assertNull(serializer.deserialize(serializer.serialize(null)));
    }
}
