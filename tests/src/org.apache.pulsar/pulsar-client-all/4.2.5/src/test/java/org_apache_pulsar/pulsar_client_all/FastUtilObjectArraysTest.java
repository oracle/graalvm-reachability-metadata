/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.objects.ObjectArrays;
import org.junit.jupiter.api.Test;

public class FastUtilObjectArraysTest {
    @Test
    void growsAnArrayWhilePreservingItsRuntimeType() {
        String[] values = ObjectArrays.forceCapacity(new String[] {"pulsar", "bookkeeper"}, 4, 2);

        assertThat(values)
                .isInstanceOf(String[].class)
                .hasSize(4)
                .startsWith("pulsar", "bookkeeper");
    }
}
