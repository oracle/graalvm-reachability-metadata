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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ConcurrentHashMultisetTest {
    @Test
    void preservesConcurrentCountsAcrossSerialization() {
        ConcurrentHashMultiset<String> original = ConcurrentHashMultiset.create();
        original.add("delivered", 4);
        original.add("pending", 2);

        ConcurrentHashMultiset<String> restored = SerializationUtils.roundtrip(original);

        assertThat(restored.count("delivered")).isEqualTo(4);
        assertThat(restored.count("pending")).isEqualTo(2);
        assertThat(restored).isEqualTo(original);
    }
}
