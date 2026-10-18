/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class ObjectOpenHashSetTest {

    @Test
    void preservesUniqueElementsIncludingNull() {
        final ObjectOpenHashSet<String> source = new ObjectOpenHashSet<>();
        source.add("one");
        source.add("two");
        source.add(null);

        final ObjectOpenHashSet<String> restored = SerializationUtils.clone(source);

        assertThat(restored).containsExactlyInAnyOrder("one", "two", null);
    }
}
