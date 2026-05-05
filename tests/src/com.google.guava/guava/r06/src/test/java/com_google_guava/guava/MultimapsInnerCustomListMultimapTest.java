/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Supplier;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomListMultimapTest {
    @Test
    void serializesCustomListMultimapFactoryAndBackingMap() throws Exception {
        Map<String, Collection<String>> backingMap = new LinkedHashMap<String, Collection<String>>();
        ListMultimap<String, String> original = Multimaps.newListMultimap(
                backingMap,
                new SerializableArrayListSupplier());
        original.put("letters", "a");
        original.put("letters", "b");
        original.put("numbers", "one");

        ListMultimap<String, String> copy = roundTrip(original);

        assertThat(copy.get("letters")).containsExactly("a", "b");
        assertThat(copy.get("numbers")).containsExactly("one");
        assertThat(copy.keySet()).containsExactly("letters", "numbers");

        copy.put("empty-before-deserialization", "created-by-restored-factory");
        assertThat(copy.get("empty-before-deserialization"))
                .containsExactly("created-by-restored-factory");
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

    private static final class SerializableArrayListSupplier
            implements Supplier<List<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public List<String> get() {
            return new ArrayList<String>();
        }
    }
}
