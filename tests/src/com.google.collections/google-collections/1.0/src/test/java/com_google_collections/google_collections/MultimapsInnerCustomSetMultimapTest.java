/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Supplier;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

public class MultimapsInnerCustomSetMultimapTest {
    @Test
    void customSetMultimapRoundTripSerializesFactoryAndBackingMap() throws Exception {
        SetMultimap<String, String> multimap = Multimaps.newSetMultimap(
                new LinkedHashMap<String, Collection<String>>(), new LinkedHashSetSupplier());
        multimap.put("letters", "a");
        multimap.put("letters", "b");
        multimap.put("letters", "a");
        multimap.put("digits", "one");

        SetMultimap<String, String> restored = roundTrip(multimap);
        restored.put("letters", "c");
        restored.put("new", "value");

        assertThat(restored.get("letters")).containsExactly("a", "b", "c");
        assertThat(restored.get("digits")).containsExactly("one");
        assertThat(restored.get("new")).containsExactly("value");
        assertThat(restored.size()).isEqualTo(5);
        assertThat(restored.asMap()).containsOnlyKeys("letters", "digits", "new");
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

    private static final class LinkedHashSetSupplier
            implements Supplier<Set<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Set<String> get() {
            return new LinkedHashSet<String>();
        }
    }
}
