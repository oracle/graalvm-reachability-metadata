/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.base.Supplier;
import org.testcontainers.shaded.com.google.common.collect.Multimap;
import org.testcontainers.shaded.com.google.common.collect.Multimaps;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class MultimapsInnerCustomMultimapTest {
    @Test
    void preservesCustomCollectionFactoriesWhenSerialized() {
        Multimap<String, String> values = Multimaps.newMultimap(new HashMap<>(), new CollectionFactory());
        values.put("key", "value");

        Multimap<?, ?> restored = (Multimap<?, ?>) SerializationUtils.roundtrip((Serializable) values);

        assertThat(restored.entries()).containsExactly(org.assertj.core.data.MapEntry.entry("key", "value"));
    }

    public static class CollectionFactory implements Supplier<Collection<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Collection<String> get() {
            return new ArrayList<>();
        }
    }
}
