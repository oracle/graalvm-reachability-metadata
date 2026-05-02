/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FastCopyHashMapTest {

    @Test
    void mdcCopySerializationRoundTripPreservesEntries() throws Exception {
        Map<String, String> originalMdc = MDC.copy();
        try {
            MDC.clear();
            Map<String, String> expectedEntries = new LinkedHashMap<>();
            expectedEntries.put("logger", "main");
            expectedEntries.put("handler", "console");
            expectedEntries.forEach(MDC::put);

            Map<String, String> map = MDC.copy();
            assertThat(map.getClass().getName()).isEqualTo("org.jboss.logmanager.FastCopyHashMap");

            byte[] serialized = serialize(map);
            Object restored = deserialize(serialized);

            assertThat(restored.getClass().getName()).isEqualTo("org.jboss.logmanager.FastCopyHashMap");
            assertThat(restored).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, String> restoredMap = (Map<String, String>) restored;
            assertThat(restoredMap).isEqualTo(expectedEntries);
        } finally {
            MDC.clear();
            originalMdc.forEach(MDC::put);
        }
    }

    private static byte[] serialize(final Object value) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
            objectOutputStream.flush();
            return outputStream.toByteArray();
        }
    }

    private static Object deserialize(final byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return objectInputStream.readObject();
        }
    }
}
