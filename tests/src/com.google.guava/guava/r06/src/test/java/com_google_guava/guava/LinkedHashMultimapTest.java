/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedHashMultimapTest {
    @Test
    void serializesEntriesAndRestoresEntryIterationOrder() throws Exception {
        LinkedHashMultimap<String, Integer> original = LinkedHashMultimap.create();
        original.put("second", 20);
        original.put("first", 10);
        original.put("second", 21);
        original.put(null, 0);
        original.put("first", null);

        SetMultimap<String, Integer> copy = roundTrip(original);

        assertThat(copy).isEqualTo(original);
        assertThat(entryDescriptions(copy)).containsExactly(
                "second=20",
                "first=10",
                "second=21",
                "null=0",
                "first=null");
        assertThat(copy.get("second")).containsExactly(20, 21);
        assertThat(copy.get("first")).containsExactly(10, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream input = new ObjectInputStream(inputBytes)) {
            return (T) input.readObject();
        }
    }

    private static List<String> entryDescriptions(SetMultimap<String, Integer> multimap) {
        List<String> descriptions = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : multimap.entries()) {
            descriptions.add(entry.getKey() + "=" + entry.getValue());
        }
        return descriptions;
    }
}
