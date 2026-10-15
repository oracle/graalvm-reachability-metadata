/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.ObjectArrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ObjectArraysTest {
    @Test
    void createsArrayWithRequestedComponentType() {
        String[] values = ObjectArrays.newArray(String.class, 3);
        values[1] = "middle";

        assertThat(values).containsExactly(null, "middle", null);
        assertThat(values.getClass().getComponentType()).isEqualTo(String.class);
    }
}
