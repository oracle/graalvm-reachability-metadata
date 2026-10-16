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
    void createsObjectAndPrimitiveArraysFromJson() {
        final Gson gson = new Gson();

        assertThat(gson.fromJson("[\"one\",\"two\"]", String[].class)).containsExactly("one", "two");
        assertThat(gson.fromJson("[1,2,3]", int[].class)).containsExactly(1, 2, 3);
    }
}
