/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.util.ArrayBuilders;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayBuildersTest {
    @Test
    void insertsValuesIntoTypedArrays() {
        assertThat(ArrayBuilders.insertInList(new String[] {"b", "c"}, "a")).containsExactly("a", "b", "c");
        assertThat(ArrayBuilders.insertInListNoDup(new String[] {"b", "c"}, "a"))
            .containsExactly("a", "b", "c");
        assertThat(ArrayBuilders.insertInListNoDup(new String[] {"a", "b"}, "a"))
            .containsExactly("a", "b");
        assertThat(ArrayBuilders.insertInListNoDup(new String[] {"b", "a", "c"}, "a"))
            .containsExactly("a", "b", "c");
    }
}
