/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.immutables.value.internal.$guava$.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class $SerializationTest {
    @Test
    void writesAndPopulatesMapEntries() throws Exception {
        final Map<String, Integer> source = new LinkedHashMap<>();
        source.put("alpha", 1);
        source.put("beta", 2);

        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteStream)) {
            $Serialization.writeMap(source, outputStream);
        }

        final Map<String, Integer> target = new LinkedHashMap<>();
        try (ObjectInputStream inputStream = new ObjectInputStream(
                new ByteArrayInputStream(byteStream.toByteArray()))) {
            $Serialization.populateMap(target, inputStream);
        }

        assertThat(target).containsExactlyEntriesOf(source);
    }

    @Test
    void writesAndPopulatesMultisetEntries() throws Exception {
        final $HashMultiset<String> source = $HashMultiset.create();
        source.add("alpha", 2);
        source.add("beta", 1);

        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteStream)) {
            $Serialization.writeMultiset(source, outputStream);
        }

        final $HashMultiset<String> target = $HashMultiset.create();
        try (ObjectInputStream inputStream = new ObjectInputStream(
                new ByteArrayInputStream(byteStream.toByteArray()))) {
            $Serialization.populateMultiset(target, inputStream);
        }

        assertThat(target.count("alpha")).isEqualTo(2);
        assertThat(target.count("beta")).isEqualTo(1);
        assertThat(target.elementSet()).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    void writesAndPopulatesMultimapEntries() throws Exception {
        final $HashMultimap<String, String> source = $HashMultimap.create();
        source.put("letters", "a");
        source.put("letters", "b");
        source.put("numbers", "1");

        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteStream)) {
            $Serialization.writeMultimap(source, outputStream);
        }

        final $HashMultimap<String, String> target = $HashMultimap.create();
        try (ObjectInputStream inputStream = new ObjectInputStream(
                new ByteArrayInputStream(byteStream.toByteArray()))) {
            $Serialization.populateMultimap(target, inputStream);
        }

        assertThat(target.get("letters")).containsExactlyInAnyOrder("a", "b");
        assertThat(target.get("numbers")).containsExactly("1");
        assertThat(target.keySet()).containsExactlyInAnyOrder("letters", "numbers");
    }

    @Test
    void getsFieldSetterForPrivateFields() {
        final FieldHolder fieldHolder = new FieldHolder();
        final $Serialization.FieldSetter<FieldHolder> textSetter =
                $Serialization.getFieldSetter(FieldHolder.class, "text");
        final $Serialization.FieldSetter<FieldHolder> countSetter =
                $Serialization.getFieldSetter(FieldHolder.class, "count");

        textSetter.set(fieldHolder, "updated");
        countSetter.set(fieldHolder, 7);

        assertThat(fieldHolder.text).isEqualTo("updated");
        assertThat(fieldHolder.count).isEqualTo(7);
    }

    private static final class FieldHolder {
        private String text = "initial";
        private int count;
    }
}
