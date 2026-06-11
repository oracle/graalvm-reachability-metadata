/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SMART_NULLS;

public class ReturnsMoreEmptyValuesTest {
    @Test
    void smartNullsAnswerReturnsEmptyArraysForUnstubbedArrayMethods() {
        ArrayValues values = Mockito.mock(ArrayValues.class, RETURNS_SMART_NULLS);

        assertThat(values.names()).isEmpty();
        assertThat(values.numbers()).isEmpty();
    }

    interface ArrayValues {
        String[] names();

        int[] numbers();
    }
}
