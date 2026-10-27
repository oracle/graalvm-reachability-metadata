/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.shaded.com.google.common.collect.ObjectArrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformTest {
    @Test
    void newArrayFromReferencePreservesComponentTypeAndRequestedLength() {
        String[] values = ObjectArrays.newArray(new String[] {"reference"}, 3);

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).hasSize(3).containsOnlyNulls();
    }
}
