/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.util.ArrayBuilders;
import org.junit.jupiter.api.Test;

public class ArrayBuildersTest {
    @Test
    void insertsNewElementAtFrontOfTypedArray() {
        String[] original = {"second", "third"};

        String[] expanded = ArrayBuilders.insertInListNoDup(original, "first");
        String[] reordered = ArrayBuilders.insertInListNoDup(expanded, "second");

        assertThat(expanded).containsExactly("first", "second", "third");
        assertThat(expanded.getClass()).isEqualTo(String[].class);
        assertThat(reordered).containsExactly("second", "first", "third");
    }
}
