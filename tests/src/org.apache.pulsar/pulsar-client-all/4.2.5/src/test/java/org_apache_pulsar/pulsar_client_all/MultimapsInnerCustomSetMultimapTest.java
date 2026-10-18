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
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSetMultimapTest {

    @Test
    @SuppressWarnings("unchecked")
    void preservesCustomSetUniquenessAndOrder() {
        final SetMultimap<String, String> source = Multimaps.newSetMultimap(new HashMap<>(), new SetFactory());
        source.put("orders", "created");
        source.put("orders", "created");
        source.put("orders", "stored");

        final SetMultimap<String, String> restored =
                (SetMultimap<String, String>) SerializationUtils.clone((Serializable) source);

        assertThat(restored.get("orders")).containsExactly("created", "stored");
    }

    private static final class SetFactory implements Supplier<Set<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Set<String> get() {
            return new LinkedHashSet<>();
        }
    }
}
