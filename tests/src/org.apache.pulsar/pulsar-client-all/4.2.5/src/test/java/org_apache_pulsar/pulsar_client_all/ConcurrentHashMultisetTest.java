/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.ConcurrentHashMultiset;
import org.junit.jupiter.api.Test;

public class ConcurrentHashMultisetTest {
    @Test
    void preservesConcurrentCountsAcrossSerialization() throws Exception {
        final ConcurrentHashMultiset<String> values = ConcurrentHashMultiset.create();
        values.add("message", 3);
        values.add("ack", 2);

        final ConcurrentHashMultiset<String> restored = SerializationTestSupport.roundTrip(values);
        assertThat(restored.count("message")).isEqualTo(3);
        assertThat(restored.count("ack")).isEqualTo(2);
    }
}
