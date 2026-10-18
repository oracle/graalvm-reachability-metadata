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
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomMultimapTest {

    @Test
    @SuppressWarnings("unchecked")
    void preservesCustomCollectionBackedValues() {
        final Multimap<String, String> source = Multimaps.newMultimap(new HashMap<>(), new CollectionFactory());
        source.put("orders", "created");
        source.put("orders", "stored");

        final Multimap<String, String> restored =
                (Multimap<String, String>) SerializationUtils.clone((Serializable) source);

        assertThat(restored.get("orders")).containsExactly("created", "stored");
    }

    private static final class CollectionFactory implements Supplier<Collection<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Collection<String> get() {
            return new ArrayList<>();
        }
    }
}
