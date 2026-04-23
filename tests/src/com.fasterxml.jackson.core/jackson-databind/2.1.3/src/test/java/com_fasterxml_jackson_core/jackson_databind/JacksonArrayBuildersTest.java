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
        String[] existing = new String[] { "a", "b" };
        String[] duplicateNotAtHead = new String[] { "b", "a", "c" };

        assertThat(ArrayBuilders.insertInList(new String[] { "b", "c" }, "a")).containsExactly("a", "b", "c");
        assertThat(ArrayBuilders.insertInListNoDup(existing, existing[0])).isSameAs(existing);
        assertThat(ArrayBuilders.insertInListNoDup(duplicateNotAtHead, duplicateNotAtHead[1]))
                .isNotNull()
                .hasSize(duplicateNotAtHead.length);
        assertThat(ArrayBuilders.insertInListNoDup(new String[] { "b", "c" }, "a")).containsExactly("a", "b", "c");
    }
}
