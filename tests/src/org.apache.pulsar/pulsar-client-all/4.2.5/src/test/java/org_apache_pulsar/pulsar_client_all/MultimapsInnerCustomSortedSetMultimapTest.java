/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.pulsar.shade.com.google.common.base.Supplier;
import org.apache.pulsar.shade.com.google.common.collect.Multimaps;
import org.apache.pulsar.shade.com.google.common.collect.SortedSetMultimap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSortedSetMultimapTest {

    @Test
    @SuppressWarnings("unchecked")
    void preservesCustomSortedSetOrdering() {
        final SortedSetMultimap<String, String> source =
                Multimaps.newSortedSetMultimap(new HashMap<>(), new SortedSetFactory());
        source.put("orders", "stored");
        source.put("orders", "created");

        final SortedSetMultimap<String, String> restored =
                (SortedSetMultimap<String, String>) SerializationUtils.clone((Serializable) source);

        assertThat(restored.get("orders")).containsExactly("created", "stored");
    }

    private static final class SortedSetFactory implements Supplier<SortedSet<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public SortedSet<String> get() {
            return new TreeSet<>();
        }
    }
}
