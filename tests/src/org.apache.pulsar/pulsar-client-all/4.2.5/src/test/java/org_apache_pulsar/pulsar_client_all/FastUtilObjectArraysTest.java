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
    void growsATypedArrayWithoutChangingItsComponentType() {
        final String[] source = new String[] {"created", "stored"};

        final String[] grown = ObjectArrays.ensureCapacity(source, 5);

        assertThat(grown.getClass()).isEqualTo(String[].class);
        assertThat(grown).startsWith("created", "stored").hasSize(5);
    }
}
