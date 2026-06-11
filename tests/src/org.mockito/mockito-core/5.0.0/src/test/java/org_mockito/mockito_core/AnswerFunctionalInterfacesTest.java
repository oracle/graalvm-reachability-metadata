/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;

public class AnswerFunctionalInterfacesTest {
    @Test
    void typedFunctionalAnswerUsesInvocationArguments() {
        Calculator calculator = Mockito.mock(Calculator.class);
        Mockito.when(calculator.add(anyInt(), anyInt()))
                .thenAnswer(
                        AdditionalAnswers.answer(
                                (Integer left, Integer right) -> left + right));

        assertThat(calculator.add(2, 3)).isEqualTo(5);
    }

    interface Calculator {
        int add(int left, int right);
    }
}
