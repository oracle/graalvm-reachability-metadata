/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_expression;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import static org.assertj.core.api.Assertions.assertThat;

public class SelectionTest {
    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Test
    void selectsPrimitiveArrayElementsToTypedResultArray() {
        Expression expression = this.parser.parseExpression("?[#this > 1]");

        Object value = expression.getValue(new int[] {1, 2, 3});

        assertThat(value)
                .isInstanceOf(Integer[].class);
        assertThat((Integer[]) value)
                .containsExactly(2, 3);
    }
}
