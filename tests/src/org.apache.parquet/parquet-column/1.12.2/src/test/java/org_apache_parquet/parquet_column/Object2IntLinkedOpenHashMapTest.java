/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_column;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import shaded.parquet.it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import shaded.parquet.it.unimi.dsi.fastutil.objects.ObjectIterator;

public class Object2IntLinkedOpenHashMapTest {
    @Test
    void javaSerializationPreservesEntriesAndLinkedIterationOrder() throws Exception {
        Object2IntLinkedOpenHashMap<String> original = new Object2IntLinkedOpenHashMap<>();
        original.put("alpha", 1);
        original.put(null, 2);
        original.put("bravo", 3);

        Object2IntLinkedOpenHashMap<String> restored = deserialize(serialize(original));

        assertEquals(3, restored.size());
        assertEquals(1, restored.getInt("alpha"));
        assertEquals(2, restored.getInt(null));
        assertEquals(3, restored.getInt("bravo"));
        assertEquals(Arrays.asList("alpha", null, "bravo"), iterationOrder(restored));
    }

    private static byte[] serialize(Object2IntLinkedOpenHashMap<String> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    private static Object2IntLinkedOpenHashMap<String> deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            @SuppressWarnings("unchecked")
            Object2IntLinkedOpenHashMap<String> restored = (Object2IntLinkedOpenHashMap<String>) input.readObject();
            return restored;
        }
    }

    private static List<String> iterationOrder(Object2IntLinkedOpenHashMap<String> map) {
        List<String> keys = new ArrayList<>();
        ObjectIterator<String> iterator = map.keySet().iterator();
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }
        return keys;
    }
}
