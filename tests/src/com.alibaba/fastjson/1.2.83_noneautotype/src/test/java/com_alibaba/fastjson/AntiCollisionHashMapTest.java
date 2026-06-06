/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.util.AntiCollisionHashMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class AntiCollisionHashMapTest {
    @Test
    @SuppressWarnings("deprecation")
    void javaSerializationRoundTripPreservesEntries() throws Exception {
        AntiCollisionHashMap<String, String> source = new AntiCollisionHashMap<>();
        source.put("name", "fastjson");

        AntiCollisionHashMap<String, String> restored = deserialize(serialize(source));

        assertThat(restored).isNotSameAs(source);
        assertThat(restored).containsEntry("name", "fastjson");
        assertThat(restored).hasSize(1);
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private static AntiCollisionHashMap<String, String> deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = in.readObject();
            assertThat(value).isInstanceOf(AntiCollisionHashMap.class);
            return (AntiCollisionHashMap<String, String>) value;
        }
    }
}
