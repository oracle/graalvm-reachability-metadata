/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static java.util.Comparator.reverseOrder;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.pulsar.shade.com.google.common.base.Supplier;
import org.apache.pulsar.shade.com.google.common.collect.Multimaps;
import org.apache.pulsar.shade.com.google.common.collect.SortedSetMultimap;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSortedSetMultimapTest {
    @Test
    void roundTripPreservesValueComparator() throws Exception {
        SortedSetMultimap<String, String> input =
                Multimaps.newSortedSetMultimap(new HashMap<>(), new SortedSetSupplier());
        input.put("letters", "a");
        input.put("letters", "c");
        input.put("letters", "b");

        SortedSetMultimap<String, String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored.get("letters")).containsExactly("c", "b", "a");
        restored.put("letters", "z");
        assertThat(restored.get("letters")).startsWith("z");
    }

    private static final class SortedSetSupplier implements Supplier<SortedSet<String>>, Serializable {
        @Override
        public SortedSet<String> get() {
            return new TreeSet<>(reverseOrder());
        }
    }
}
