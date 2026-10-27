/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.pulsar.shade.com.google.common.base.Supplier;
import org.apache.pulsar.shade.com.google.common.collect.ListMultimap;
import org.apache.pulsar.shade.com.google.common.collect.Multimaps;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomListMultimapTest {
    @Test
    void roundTripPreservesListOrderAndDuplicates() throws Exception {
        ListMultimap<String, String> input = Multimaps.newListMultimap(new HashMap<>(), new ListSupplier());
        input.put("letters", "a");
        input.put("letters", "b");
        input.put("letters", "a");

        ListMultimap<String, String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored.get("letters")).containsExactly("a", "b", "a");
        restored.put("letters", "c");
        assertThat(restored.get("letters")).endsWith("c");
    }

    private static final class ListSupplier implements Supplier<List<String>>, Serializable {
        @Override
        public List<String> get() {
            return new ArrayList<>();
        }
    }
}
