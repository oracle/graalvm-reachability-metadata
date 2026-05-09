/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_column;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import shaded.parquet.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class ObjectArrayListTest {
    @Test
    void javaSerializationPreservesElementsAndOrder() throws Exception {
        ObjectArrayList<String> original = new ObjectArrayList<>();
        original.add("alpha");
        original.add(null);
        original.add("bravo");

        ObjectArrayList<String> restored = deserialize(serialize(original));

        assertEquals(3, restored.size());
        assertEquals("alpha", restored.get(0));
        assertNull(restored.get(1));
        assertEquals("bravo", restored.get(2));
    }

    private static byte[] serialize(ObjectArrayList<String> list) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(list);
        }
        return bytes.toByteArray();
    }

    private static ObjectArrayList<String> deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            @SuppressWarnings("unchecked")
            ObjectArrayList<String> restored = (ObjectArrayList<String>) input.readObject();
            return restored;
        }
    }
}
