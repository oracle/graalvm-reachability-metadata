/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import scala.collection.concurrent.TrieMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises supported serialization behavior. §FS-repository-functional-spec.5.2 */
public class TrieMapTest {

    @Test
    void serializesAndDeserializesEntries() throws Exception {
        TrieMap<String, Integer> original = new TrieMap<>();
        original.put("first", 1);
        original.put("second", 2);

        byte[] serialized = serialize(original);
        Object restoredObject;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            restoredObject = input.readObject();
        }

        assertThat(restoredObject).isInstanceOf(TrieMap.class);
        @SuppressWarnings("unchecked")
        TrieMap<String, Integer> restored = (TrieMap<String, Integer>) restoredObject;
        assertThat(restored.size()).isEqualTo(2);
        assertThat(restored.get("first").get()).isEqualTo(1);
        assertThat(restored.get("second").get()).isEqualTo(2);
    }

    private static byte[] serialize(TrieMap<String, Integer> map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }
}
