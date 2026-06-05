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

public class ArrayBuildersTest {

    @Test
    void movesExistingElementToFrontWithoutChangingArrayLength() {
        String first = "first";
        String second = "second";
        String third = "third";
        String[] original = {first, second, third};

        String[] reordered = ArrayBuilders.insertInListNoDup(original, second);

        assertThat(reordered).isNotSameAs(original);
        assertThat(reordered).containsExactly(second, first, third);
        assertThat(reordered.getClass()).isSameAs(String[].class);
    }
}
