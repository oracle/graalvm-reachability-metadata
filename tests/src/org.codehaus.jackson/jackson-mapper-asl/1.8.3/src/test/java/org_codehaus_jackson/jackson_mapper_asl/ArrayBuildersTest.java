/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import org.codehaus.jackson.map.util.ArrayBuilders;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayBuildersTest {
    @Test
    void insertInListPrependsElementToNewArray() {
        String[] existingValues = new String[] {"second", "third"};

        String[] result = ArrayBuilders.insertInList(existingValues, "first");

        assertThat(result)
                .isNotSameAs(existingValues)
                .containsExactly("first", "second", "third");
        assertThat(existingValues).containsExactly("second", "third");
    }

    @Test
    void insertInListNoDupReturnsOriginalWhenElementIsAlreadyFirst() {
        String firstValue = "first";
        String[] existingValues = new String[] {firstValue, "second"};

        String[] result = ArrayBuilders.insertInListNoDup(existingValues, firstValue);

        assertThat(result).isSameAs(existingValues);
    }

    @Test
    void insertInListNoDupMovesExistingElementFromLaterPosition() {
        String promotedValue = "promoted";
        String[] existingValues = new String[] {"first", promotedValue, "third"};

        String[] result = ArrayBuilders.insertInListNoDup(existingValues, promotedValue);

        assertThat(result)
                .isNotSameAs(existingValues)
                .hasSize(existingValues.length);
        assertThat(existingValues[0]).isSameAs(promotedValue);
    }

    @Test
    void insertInListNoDupPrependsElementWhenNoDuplicateExists() {
        String[] existingValues = new String[] {"second", "third"};

        String[] result = ArrayBuilders.insertInListNoDup(existingValues, "first");

        assertThat(result)
                .isNotSameAs(existingValues)
                .containsExactly("first", "second", "third");
        assertThat(existingValues).containsExactly("second", "third");
    }
}
