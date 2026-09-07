/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.base.Supplier;
import org.testcontainers.shaded.com.google.common.collect.Multimaps;
import org.testcontainers.shaded.com.google.common.collect.SortedSetMultimap;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

public class MultimapsInnerCustomSortedSetMultimapTest {
    @Test
    @SuppressWarnings("unchecked")
    void preservesCustomSortedSetFactoriesWhenSerialized() {
        SortedSetMultimap<String, String> values = Multimaps.newSortedSetMultimap(
            new HashMap<>(),
            new SortedSetFactory()
        );
        values.put("key", "two");
        values.put("key", "one");

        SortedSetMultimap<String, String> restored = (SortedSetMultimap<String, String>) SerializationUtils.roundtrip(
            (Serializable) values
        );

        assertThat(restored.asMap().get("key")).containsExactly("one", "two");
    }

    public static class SortedSetFactory implements Supplier<SortedSet<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public SortedSet<String> get() {
            return new TreeSet<>();
        }
    }
}
