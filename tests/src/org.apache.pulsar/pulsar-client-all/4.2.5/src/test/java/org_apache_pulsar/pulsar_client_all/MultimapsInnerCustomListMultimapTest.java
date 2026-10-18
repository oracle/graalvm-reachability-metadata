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
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomListMultimapTest {

    @Test
    @SuppressWarnings("unchecked")
    void preservesCustomListOrderAndDuplicates() {
        final ListMultimap<String, String> source = Multimaps.newListMultimap(new HashMap<>(), new ListFactory());
        source.put("orders", "created");
        source.put("orders", "created");
        source.put("orders", "stored");

        final ListMultimap<String, String> restored =
                (ListMultimap<String, String>) SerializationUtils.clone((Serializable) source);

        assertThat(restored.get("orders")).containsExactly("created", "created", "stored");
    }

    private static final class ListFactory implements Supplier<List<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public List<String> get() {
            return new ArrayList<>();
        }
    }
}
