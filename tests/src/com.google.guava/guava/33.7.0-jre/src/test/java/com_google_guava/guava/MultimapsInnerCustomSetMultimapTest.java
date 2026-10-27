/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Supplier;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSetMultimapTest {
    @Test
    void serializesCustomSetMultimapFactoryAndBackingMap() throws Exception {
        Map<String, Collection<String>> backingMap =
                new LinkedHashMap<String, Collection<String>>();
        SetMultimap<String, String> original = Multimaps.newSetMultimap(
                backingMap,
                new SerializableLinkedHashSetSupplier());
        original.put("letters", "a");
        original.put("letters", "b");
        original.put("letters", "a");
        original.put("numbers", "one");

        SetMultimap<String, String> copy = roundTrip(original);

        assertThat(copy.get("letters")).containsExactly("a", "b");
        assertThat(copy.get("numbers")).containsExactly("one");
        assertThat(copy.keySet()).containsExactly("letters", "numbers");

        copy.put("created-after-deserialization", "uses-restored-factory");
        copy.put("created-after-deserialization", "uses-restored-factory");
        copy.put("created-after-deserialization", "second-value");
        assertThat(copy.get("created-after-deserialization"))
                .containsExactly("uses-restored-factory", "second-value");
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

    private static final class SerializableLinkedHashSetSupplier
            implements Supplier<Set<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Set<String> get() {
            return new LinkedHashSet<String>();
        }
    }
}
