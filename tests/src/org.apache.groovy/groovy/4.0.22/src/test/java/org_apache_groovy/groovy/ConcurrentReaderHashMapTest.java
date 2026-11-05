/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.metaclass.ConcurrentReaderHashMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConcurrentReaderHashMapTest {
    @Test
    void restoresMappingsAndRemainsMutableAfterSerialization() throws Exception {
        ConcurrentReaderHashMap original = new ConcurrentReaderHashMap();
        original.put("red", "warm");
        original.put("blue", "cool");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ConcurrentReaderHashMap restored = (ConcurrentReaderHashMap) input.readObject();
            assertEquals(Map.of("red", "warm", "blue", "cool"), restored);
            restored.put("green", "neutral");
            assertEquals("neutral", restored.get("green"));
        }
    }
}
