/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.apache.groovy.util.concurrent.ConcurrentReferenceHashMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConcurrentReferenceHashMapTest {
    @Test
    void preservesEntriesAcrossSerialization() throws Exception {
        ConcurrentReferenceHashMap<String, String> original = new ConcurrentReferenceHashMap<>();
        original.put("east", "open");
        original.put("west", "closed");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(Map.of("east", "open", "west", "closed"), input.readObject());
        }
    }
}
