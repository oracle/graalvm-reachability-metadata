/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.pulsar.shade.com.google.common.collect.ArrayTable;
import org.junit.jupiter.api.Test;

public class ArrayTableTest {

    @Test
    void exportsValuesAsATypedMatrix() {
        final ArrayTable<String, String, String> table =
                ArrayTable.create(List.of("broker-a", "broker-b"), List.of("state", "region"));
        table.put("broker-a", "state", "ready");
        table.put("broker-a", "region", "east");
        table.put("broker-b", "state", "standby");
        table.put("broker-b", "region", "west");

        final String[][] values = table.toArray(String.class);

        assertThat(values).hasDimensions(2, 2);
        assertThat(values[0]).containsExactly("ready", "east");
        assertThat(values[1]).containsExactly("standby", "west");
    }
}
