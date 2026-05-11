/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.diffplug.common.collect.Multimap;
import com.diffplug.common.collect.Multimaps;

public class MultimapsInnerCustomMultimapTest {
    @Test
    void serializesFactoryAndBackingMapForCustomMultimap() throws Exception {
        Map<String, Collection<String>> backingMap = new LinkedHashMap<>();
        Multimap<String, String> original = Multimaps.newMultimap(backingMap, new ArrayDequeFactory());
        original.put("letters", "a");
        original.put("letters", "b");
        original.put("digits", "1");

        @SuppressWarnings("unchecked")
        Multimap<String, String> copy = (Multimap<String, String>) roundTrip(original);
        copy.put("letters", "c");
        copy.put("symbols", "!");

        assertThat(copy.get("letters")).containsExactly("a", "b", "c");
        assertThat(copy.get("digits")).containsExactly("1");
        assertThat(copy.get("symbols")).containsExactly("!");
        assertThat(copy.asMap().keySet()).containsExactly("letters", "digits", "symbols");
    }

    private static Object roundTrip(Object original) throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return input.readObject();
        }
    }

    private static final class ArrayDequeFactory implements Supplier<Collection<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Collection<String> get() {
            return new ArrayDeque<>();
        }
    }
}
