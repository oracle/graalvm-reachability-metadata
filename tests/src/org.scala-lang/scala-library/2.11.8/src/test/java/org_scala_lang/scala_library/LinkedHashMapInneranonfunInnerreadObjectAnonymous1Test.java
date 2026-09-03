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

import scala.collection.Iterator;
import scala.collection.mutable.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedHashMapInneranonfunInnerreadObjectAnonymous1Test {

    @Test
    void restoresLinkedMapEntriesInInsertionOrder() throws Exception {
        LinkedHashMap<String, Integer> original = new LinkedHashMap<>();
        original.put("one", 1);
        original.put("two", 2);

        LinkedHashMap<String, Integer> restored = roundTrip(original);
        Iterator<String> keys = restored.keysIterator();

        assertThat(restored.get("one").get()).isEqualTo(1);
        assertThat(restored.get("two").get()).isEqualTo(2);
        assertThat(keys.next()).isEqualTo("one");
        assertThat(keys.next()).isEqualTo("two");
        assertThat(keys.hasNext()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
