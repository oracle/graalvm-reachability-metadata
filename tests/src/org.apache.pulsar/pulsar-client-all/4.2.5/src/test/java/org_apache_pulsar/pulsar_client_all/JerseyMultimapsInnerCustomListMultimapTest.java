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
import java.util.function.Supplier;
import org.apache.pulsar.shade.org.glassfish.jersey.internal.guava.ListMultimap;
import org.apache.pulsar.shade.org.glassfish.jersey.internal.guava.Multimaps;
import org.junit.jupiter.api.Test;

public class JerseyMultimapsInnerCustomListMultimapTest {
    @Test
    void roundTripPreservesListOrderAndDuplicates() throws Exception {
        ListMultimap<String, String> input =
                Multimaps.newListMultimap(new HashMap<>(), new ListSupplier());
        input.put("topics", "persistent");
        input.put("topics", "non-persistent");
        input.put("topics", "persistent");

        ListMultimap<String, String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored.get("topics"))
                .containsExactly("persistent", "non-persistent", "persistent");
    }

    private static final class ListSupplier implements Supplier<List<String>>, Serializable {
        @Override
        public List<String> get() {
            return new ArrayList<>();
        }
    }
}
