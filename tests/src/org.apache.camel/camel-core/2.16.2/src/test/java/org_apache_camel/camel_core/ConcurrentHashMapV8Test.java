/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class ConcurrentHashMapV8Test {
    private static final String MAP_CLASS_NAME =
            "org.apache.camel.com.googlecode.concurrentlinkedhashmap.ConcurrentHashMapV8";

    @Test
    void serializesAndDeserializesEntries() throws Throwable {
        Map<String, String> map = newConcurrentHashMapV8();
        map.put("first", "alpha");
        map.put("second", "bravo");
        map.put("third", "charlie");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }

        Object deserialized;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            deserialized = input.readObject();
        }

        assertThat(deserialized).isInstanceOf(Map.class);
        assertThat(deserialized.getClass().getName()).isEqualTo(MAP_CLASS_NAME);
        @SuppressWarnings("unchecked")
        Map<String, String> restored = (Map<String, String>) deserialized;
        assertThat(restored).hasSize(3);
        assertThat(restored).containsEntry("first", "alpha");
        assertThat(restored).containsEntry("second", "bravo");
        assertThat(restored).containsEntry("third", "charlie");
    }

    private static Map<String, String> newConcurrentHashMapV8() throws Throwable {
        Class<?> mapClass = Class.forName(MAP_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(mapClass, MethodHandles.lookup());
        MethodHandle constructor = lookup.findConstructor(mapClass, MethodType.methodType(void.class));
        Object instance = constructor.invoke();

        assertThat(instance).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) instance;
        return map;
    }
}
