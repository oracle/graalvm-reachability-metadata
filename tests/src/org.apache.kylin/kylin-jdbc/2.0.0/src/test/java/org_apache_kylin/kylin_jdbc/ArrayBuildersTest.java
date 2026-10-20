/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.util.ArrayBuilders;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayBuildersTest {
    @Test
    void insertsValuesWhilePreservingTheArrayComponentType() {
        String[] inserted = ArrayBuilders.insertInList(new String[] {"second"}, "first");
        String[] withoutDuplicate = ArrayBuilders.insertInListNoDup(inserted, "second");
        String[] movedToFront = ArrayBuilders.insertInListNoDup(withoutDuplicate, "second");

        assertThat(inserted).containsExactly("first", "second");
        assertThat(withoutDuplicate).containsExactly("first", "second");
        assertThat(movedToFront).containsExactly("second", "first");
        assertThat(movedToFront.getClass()).isEqualTo(String[].class);
    }
}
