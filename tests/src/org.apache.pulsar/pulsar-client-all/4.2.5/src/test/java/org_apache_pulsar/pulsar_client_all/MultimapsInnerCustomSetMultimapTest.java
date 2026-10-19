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
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.pulsar.shade.com.google.common.base.Supplier;
import org.apache.pulsar.shade.com.google.common.collect.Multimaps;
import org.apache.pulsar.shade.com.google.common.collect.SetMultimap;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSetMultimapTest {
    @Test
    void roundTripPreservesDistinctInsertionOrderedValues() throws Exception {
        SetMultimap<String, String> input = Multimaps.newSetMultimap(new HashMap<>(), new SetSupplier());
        input.put("letters", "b");
        input.put("letters", "a");
        input.put("letters", "b");

        SetMultimap<String, String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored.get("letters")).containsExactly("b", "a");
        restored.put("letters", "c");
        assertThat(restored.get("letters")).containsExactly("b", "a", "c");
    }

    private static final class SetSupplier implements Supplier<Set<String>>, Serializable {
        @Override
        public Set<String> get() {
            return new LinkedHashSet<>();
        }
    }
}
