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

public class ConstructorReferenceTest {
    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Test
    void createsOneDimensionalPrimitiveArrayFromDeclaredSize() {
        Expression expression = this.parser.parseExpression("new int[3]");

        int[] values = expression.getValue(int[].class);

        assertThat(values)
                .containsExactly(0, 0, 0);
    }

    @Test
    void createsMultiDimensionalPrimitiveArrayFromDeclaredSizes() {
        Expression expression = this.parser.parseExpression("new int[2][3]");

        int[][] values = expression.getValue(int[][].class);

        assertThat(values.length)
                .isEqualTo(2);
        assertThat(values[0])
                .containsExactly(0, 0, 0);
        assertThat(values[1])
                .containsExactly(0, 0, 0);
    }

    @Test
    void createsPrimitiveArrayFromInlineInitializer() {
        Expression expression = this.parser.parseExpression("new int[] {1, 2, 3}");

        int[] values = expression.getValue(int[].class);

        assertThat(values)
                .containsExactly(1, 2, 3);
    }

    @Test
    void createsReferenceArrayFromInlineInitializer() {
        Expression expression = this.parser.parseExpression("new String[] {'alpha', 'beta'}");

        String[] values = expression.getValue(String[].class);

        assertThat(values)
                .containsExactly("alpha", "beta");
    }
}
