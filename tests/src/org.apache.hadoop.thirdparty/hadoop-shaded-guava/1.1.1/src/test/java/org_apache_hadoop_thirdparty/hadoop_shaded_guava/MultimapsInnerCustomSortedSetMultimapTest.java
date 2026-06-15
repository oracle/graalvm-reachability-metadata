/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.hadoop.thirdparty.com.google.common.base.Supplier;
import org.apache.hadoop.thirdparty.com.google.common.collect.Multimaps;
import org.apache.hadoop.thirdparty.com.google.common.collect.SortedSetMultimap;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSortedSetMultimapTest {
    @Test
    void roundTripsCustomSortedSetMultimapWithSerializableFactoryAndBackingMap() throws Exception {
        Map<String, Collection<String>> backingMap = new LinkedHashMap<>();
        SortedSetMultimap<String, String> original = Multimaps.newSortedSetMultimap(
                backingMap,
                StringTreeSetSupplier.INSTANCE);
        original.put("letters", "beta");
        original.put("letters", "alpha");
        original.put("letters", "beta");
        original.put("numbers", "two");
        original.put("numbers", "one");

        @SuppressWarnings("unchecked")
        SortedSetMultimap<String, String> restored = (SortedSetMultimap<String, String>) roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.get("letters")).containsExactly("alpha", "beta");
        assertThat(restored.get("numbers")).containsExactly("one", "two");
        assertThat(restored.valueComparator()).isNull();

        restored.put("letters", "gamma");
        restored.put("letters", "alpha");
        assertThat(restored.get("letters")).containsExactly("alpha", "beta", "gamma");
    }

    private static Object roundTrip(Object value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            return inputStream.readObject();
        }
    }

    private enum StringTreeSetSupplier implements Supplier<SortedSet<String>> {
        INSTANCE;

        @Override
        public SortedSet<String> get() {
            return new TreeSet<>();
        }
    }
}
