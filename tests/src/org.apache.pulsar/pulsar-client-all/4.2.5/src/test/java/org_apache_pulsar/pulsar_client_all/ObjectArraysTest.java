/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.pulsar.shade.com.google.common.collect.Iterables;
import org.junit.jupiter.api.Test;

public class ObjectArraysTest {
    @Test
    void convertsAnIterableToARequestedArrayType() {
        String[] values = Iterables.toArray(List.of("pulsar", "bookkeeper"), String.class);

        assertThat(values).isInstanceOf(String[].class).containsExactly("pulsar", "bookkeeper");
    }
}
