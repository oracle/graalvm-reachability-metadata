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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.thirdparty.com.google.common.base.Supplier;
import org.apache.hadoop.thirdparty.com.google.common.collect.Multimaps;
import org.apache.hadoop.thirdparty.com.google.common.collect.SetMultimap;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSetMultimapTest {
    @Test
    void roundTripsCustomSetMultimapWithSerializableFactoryAndBackingMap() throws Exception {
        Map<String, Collection<String>> backingMap = new LinkedHashMap<>();
        SetMultimap<String, String> original = Multimaps.newSetMultimap(
                backingMap,
                StringLinkedHashSetSupplier.INSTANCE);
        original.put("letters", "alpha");
        original.put("letters", "beta");
        original.put("letters", "alpha");
        original.put("numbers", "one");

        @SuppressWarnings("unchecked")
        SetMultimap<String, String> restored = (SetMultimap<String, String>) roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.get("letters")).containsExactly("alpha", "beta");

        restored.put("numbers", "two");
        restored.put("numbers", "one");
        assertThat(restored.get("numbers")).containsExactly("one", "two");
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

    private enum StringLinkedHashSetSupplier implements Supplier<Set<String>> {
        INSTANCE;

        @Override
        public Set<String> get() {
            return new LinkedHashSet<>();
        }
    }
}
