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
        String[] uniqueInsertion = ArrayBuilders.insertInListNoDup(inserted, "third");
        String[] movedToFront = ArrayBuilders.insertInListNoDup(inserted, "second");

        assertThat(inserted).containsExactly("first", "second");
        assertThat(uniqueInsertion).containsExactly("third", "first", "second");
        assertThat(movedToFront).containsExactly("second", "first");
        assertThat(movedToFront.getClass()).isEqualTo(String[].class);
    }
}
