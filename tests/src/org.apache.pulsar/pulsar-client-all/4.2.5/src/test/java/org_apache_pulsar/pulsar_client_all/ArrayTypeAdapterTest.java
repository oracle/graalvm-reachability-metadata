/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class ArrayTypeAdapterTest {

    @Test
    void createsATypedArrayFromJsonValues() {
        final String[] values = new Gson().fromJson("[\"created\",\"stored\"]", String[].class);

        assertThat(values).containsExactly("created", "stored");
        assertThat(values.getClass()).isEqualTo(String[].class);
    }
}
