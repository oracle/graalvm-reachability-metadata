/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.shaded.com.google.common.collect.ArrayTable;
import org.apache.curator.shaded.com.google.common.collect.ImmutableList;
import org.apache.curator.shaded.com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayCollectionsTest {
    @Test
    void convertsPublicCollectionsToTypedArrays() {
        ArrayTable<String, String, Integer> table = ArrayTable.create(
                ImmutableSet.of("row"), ImmutableSet.of("column"));
        table.put("row", "column", 7);

        assertThat(table.toArray(Integer.class)[0][0]).isEqualTo(7);
        assertThat(ImmutableList.of("one", "two").toArray(new String[0]))
                .containsExactly("one", "two");
    }
}
