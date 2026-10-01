/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

import org.apache.curator.shaded.com.google.common.base.Supplier;
import org.apache.curator.shaded.com.google.common.collect.Multimap;
import org.apache.curator.shaded.com.google.common.collect.Multimaps;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomMultimapSerializationTest {
    @Test
    void preservesCustomMultimapImplementationsWhenRoundTripped() throws Exception {
        Multimap<String, Integer> list = Multimaps.newListMultimap(new HashMap<>(), new ListSupplier());
        Multimap<String, Integer> custom = Multimaps.newMultimap(new HashMap<>(), new ListSupplier());
        Multimap<String, Integer> set = Multimaps.newSetMultimap(new HashMap<>(), new SetSupplier());
        Multimap<String, Integer> sorted = Multimaps.newSortedSetMultimap(new HashMap<>(), new SortedSetSupplier());
        list.put("key", 1);
        custom.put("key", 1);
        set.put("key", 1);
        sorted.put("key", 1);

        assertThat(roundTrip(list)).isEqualTo(list);
        assertThat(roundTrip(custom)).isEqualTo(custom);
        assertThat(roundTrip(set)).isEqualTo(set);
        assertThat(roundTrip(sorted)).isEqualTo(sorted);
    }

    private static final class ListSupplier implements Supplier<ArrayList<Integer>>, Serializable {
        @Override
        public ArrayList<Integer> get() {
            return new ArrayList<>();
        }
    }

    private static final class SetSupplier implements Supplier<HashSet<Integer>>, Serializable {
        @Override
        public HashSet<Integer> get() {
            return new HashSet<>();
        }
    }

    private static final class SortedSetSupplier implements Supplier<TreeSet<Integer>>, Serializable {
        @Override
        public TreeSet<Integer> get() {
            return new TreeSet<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
