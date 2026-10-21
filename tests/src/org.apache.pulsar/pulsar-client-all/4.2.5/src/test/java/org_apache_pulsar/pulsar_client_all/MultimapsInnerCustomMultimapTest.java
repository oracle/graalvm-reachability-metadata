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
import java.util.Collection;
import java.util.HashMap;

import org.apache.pulsar.shade.com.google.common.base.Supplier;
import org.apache.pulsar.shade.com.google.common.collect.Multimap;
import org.apache.pulsar.shade.com.google.common.collect.Multimaps;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomMultimapTest {
    @Test
    void roundTripPreservesFactoryAndValues() throws Exception {
        Multimap<String, String> input = Multimaps.newMultimap(new HashMap<>(), new CollectionSupplier());
        input.put("letters", "a");
        input.put("letters", "b");

        Multimap<String, String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored.get("letters")).containsExactly("a", "b");
        restored.put("numbers", "one");
        assertThat(restored.get("numbers")).containsExactly("one");
    }

    private static final class CollectionSupplier implements Supplier<Collection<String>>, Serializable {
        @Override
        public Collection<String> get() {
            return new ArrayList<>();
        }
    }
}
