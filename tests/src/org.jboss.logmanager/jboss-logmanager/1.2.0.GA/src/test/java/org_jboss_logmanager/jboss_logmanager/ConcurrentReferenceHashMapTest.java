/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentReferenceHashMapTest {

    @Test
    void serializationRoundTripPreservesEntries() throws Exception {
        Map<String, String> map = newConcurrentReferenceHashMap();
        Map<String, String> expectedEntries = new LinkedHashMap<>();
        expectedEntries.put("logger", "main");
        expectedEntries.put("handler", "console");
        map.putAll(expectedEntries);

        byte[] serialized;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
            objectOutputStream.flush();
            serialized = outputStream.toByteArray();
        }

        Object restored;
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            restored = objectInputStream.readObject();
        }

        assertThat(restored.getClass().getName()).isEqualTo("org.jboss.logmanager.ConcurrentReferenceHashMap");
        @SuppressWarnings("unchecked")
        Map<String, String> restoredMap = (Map<String, String>) restored;
        assertThat(restoredMap)
                .hasSize(expectedEntries.size())
                .containsAllEntriesOf(expectedEntries);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Map<String, String> newConcurrentReferenceHashMap() throws Exception {
        Class<?> mapType = Class.forName("org.jboss.logmanager.ConcurrentReferenceHashMap");
        Class enumType = Class.forName("org.jboss.logmanager.ConcurrentReferenceHashMap$ReferenceType");
        Object strongReference = Enum.valueOf(enumType, "STRONG");
        Constructor<?> constructor = mapType.getDeclaredConstructor(int.class, enumType, enumType);
        constructor.setAccessible(true);
        return (Map<String, String>) constructor.newInstance(4, strongReference, strongReference);
    }
}
