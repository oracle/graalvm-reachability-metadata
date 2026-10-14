/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.base.Supplier;
import autovalue.shaded.com.google.common.collect.Multimaps;
import autovalue.shaded.com.google.common.collect.SortedSetMultimap;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSortedSetMultimapTest {
    @Test
    void restoresCustomSortedSetFactory() throws Exception {
        HashMap<String, Collection<String>> backingMap = new HashMap<>();
        SortedSetMultimap<String, String> values =
                Multimaps.newSortedSetMultimap(backingMap, new SortedSetFactory());
        values.put("letters", "b");
        values.put("letters", "a");

        SortedSetMultimap<String, String> restored = SerializationSupport.roundTrip(values);

        assertThat(restored.get("letters")).containsExactly("a", "b");
        assertThat(restored.valueComparator()).isEqualTo(restored.get("letters").comparator());
    }

    private static final class SortedSetFactory implements Supplier<SortedSet<String>>, Serializable {
        @Override
        public SortedSet<String> get() {
            return new TreeSet<>();
        }
    }
}
