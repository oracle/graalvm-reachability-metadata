/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_collect;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import com.diffplug.common.collect.LinkedListMultimap;

public class LinkedListMultimapTest {
    @Test
    void serializesEntriesInGlobalInsertionOrder() throws Exception {
        LinkedListMultimap<String, String> original = LinkedListMultimap.create();
        original.put("letters", "a");
        original.put("digits", "1");
        original.put("letters", "b");
        original.put("symbols", "!");

        LinkedListMultimap<String, String> copy = roundTrip(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.entries()).containsExactly(
                entry("letters", "a"),
                entry("digits", "1"),
                entry("letters", "b"),
                entry("symbols", "!"));
        assertThat(copy.keys()).containsExactly("letters", "digits", "letters", "symbols");
        assertThat(copy.get("letters")).containsExactly("a", "b");
        assertThat(copy.keySet()).containsExactly("letters", "digits", "symbols");
    }

    private static LinkedListMultimap<String, String> roundTrip(LinkedListMultimap<String, String> original)
            throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.flush();
            serialized = bytes.toByteArray();
        }

        Object copy;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            copy = input.readObject();
        }

        assertThat(copy).isInstanceOf(LinkedListMultimap.class);
        @SuppressWarnings("unchecked")
        LinkedListMultimap<String, String> typedCopy = (LinkedListMultimap<String, String>) copy;
        return typedCopy;
    }
}
