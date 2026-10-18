/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.apache.pulsar.shade.org.glassfish.jersey.internal.util.collection.KeyComparator;
import org.apache.pulsar.shade.org.glassfish.jersey.internal.util.collection.KeyComparatorHashMap;
import org.junit.jupiter.api.Test;

public class KeyComparatorHashMapTest {

    @Test
    void preservesMappingsWithACaseInsensitiveComparator() {
        final KeyComparatorHashMap<String, Integer> source = new KeyComparatorHashMap<>(new CaseInsensitiveKeys());
        source.put("Content-Type", 1);
        source.put("X-Pulsar-Topic", 2);

        final KeyComparatorHashMap<String, Integer> restored = SerializationUtils.clone(source);

        assertThat(restored.get("content-type")).isEqualTo(1);
        assertThat(restored.get("x-pulsar-topic")).isEqualTo(2);
    }

    private static final class CaseInsensitiveKeys implements KeyComparator<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean equals(String left, String right) {
            return left.equalsIgnoreCase(right);
        }

        @Override
        public int hash(String key) {
            return key.toLowerCase(Locale.ROOT).hashCode();
        }
    }
}
