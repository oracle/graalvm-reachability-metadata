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

import com.diffplug.common.collect.LinkedHashMultimap;

public class LinkedHashMultimapTest {
    @Test
    void serializesDistinctKeysAndEntriesInInsertionOrder() throws Exception {
        LinkedHashMultimap<String, String> original = LinkedHashMultimap.create();
        original.put("letters", "a");
        original.put("digits", "1");
        original.put("letters", "b");
        original.put("symbols", "!");

        LinkedHashMultimap<String, String> copy = roundTrip(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.asMap().keySet()).containsExactly("letters", "digits", "symbols");
        assertThat(copy.get("letters")).containsExactly("a", "b");
        assertThat(copy.get("digits")).containsExactly("1");
        assertThat(copy.get("symbols")).containsExactly("!");
        assertThat(copy.entries()).containsExactly(
                entry("letters", "a"),
                entry("digits", "1"),
                entry("letters", "b"),
                entry("symbols", "!"));
    }

    private static LinkedHashMultimap<String, String> roundTrip(LinkedHashMultimap<String, String> original)
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

        assertThat(copy).isInstanceOf(LinkedHashMultimap.class);
        @SuppressWarnings("unchecked")
        LinkedHashMultimap<String, String> typedCopy = (LinkedHashMultimap<String, String>) copy;
        return typedCopy;
    }
}
