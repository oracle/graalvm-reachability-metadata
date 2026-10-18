/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.ConcurrentHashMultiset;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class ConcurrentHashMultisetTest {

    @Test
    void preservesConcurrentElementCounts() {
        final ConcurrentHashMultiset<String> source = ConcurrentHashMultiset.create();
        source.add("received", 4);
        source.add("acknowledged", 2);

        final ConcurrentHashMultiset<String> restored = SerializationUtils.clone(source);

        assertThat(restored.count("received")).isEqualTo(4);
        assertThat(restored.count("acknowledged")).isEqualTo(2);
    }
}
