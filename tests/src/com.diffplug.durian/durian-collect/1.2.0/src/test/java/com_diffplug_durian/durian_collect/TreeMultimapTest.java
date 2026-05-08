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

import com.diffplug.common.collect.Ordering;
import com.diffplug.common.collect.TreeMultimap;

public class TreeMultimapTest {
    @Test
    void serializesComparatorsAndSortedEntries() throws Exception {
        TreeMultimap<String, String> original = TreeMultimap.create(
                Ordering.<String>natural().reverse(),
                Ordering.<String>natural().reverse());
        original.put("b", "2");
        original.put("b", "1");
        original.put("a", "3");
        original.put("c", "0");

        TreeMultimap<String, String> copy = roundTrip(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.entries()).containsExactly(
                entry("c", "0"),
                entry("b", "2"),
                entry("b", "1"),
                entry("a", "3"));
        assertThat(copy.keyComparator().compare("a", "b")).isPositive();
        assertThat(copy.valueComparator().compare("1", "2")).isPositive();
    }

    private static TreeMultimap<String, String> roundTrip(TreeMultimap<String, String> original)
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

        assertThat(copy).isInstanceOf(TreeMultimap.class);
        @SuppressWarnings("unchecked")
        TreeMultimap<String, String> typedCopy = (TreeMultimap<String, String>) copy;
        return typedCopy;
    }
}
