/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.EnumMultiset;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class EnumMultisetTest {
    @Test
    void preservesEnumCountsAcrossSerialization() {
        EnumMultiset<Status> original = EnumMultiset.create(Status.class);
        original.add(Status.READY, 3);
        original.add(Status.PENDING, 1);

        EnumMultiset<Status> restored = SerializationUtils.roundtrip(original);

        assertThat(restored.count(Status.READY)).isEqualTo(3);
        assertThat(restored.count(Status.PENDING)).isEqualTo(1);
        assertThat(restored).isEqualTo(original);
    }

    private enum Status {
        READY,
        PENDING
    }
}
