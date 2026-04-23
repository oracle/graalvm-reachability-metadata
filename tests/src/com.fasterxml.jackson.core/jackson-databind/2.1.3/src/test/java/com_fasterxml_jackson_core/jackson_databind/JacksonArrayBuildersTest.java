/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.databind.util.ArrayBuilders;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonArrayBuildersTest {

    @Test
    void arrayBuildersInsertElementsWithAndWithoutDuplicates() {
        assertThat(ArrayBuilders.insertInList(new String[] { "b", "c" }, "a")).containsExactly("a", "b", "c");
        assertThat(ArrayBuilders.insertInListNoDup(new String[] { "b", "a" }, "a")).containsExactly("a", "b");
        assertThat(ArrayBuilders.insertInListNoDup(new String[] { "b", "c" }, "a")).containsExactly("a", "b", "c");
    }
}
