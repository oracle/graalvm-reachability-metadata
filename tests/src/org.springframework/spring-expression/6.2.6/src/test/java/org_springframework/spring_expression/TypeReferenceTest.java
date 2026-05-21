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

public class TypeReferenceTest {
    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Test
    void resolvesPrimitiveArrayTypeReference() {
        Expression expression = this.parser.parseExpression("T(int[])");

        Class<?> type = expression.getValue(Class.class);

        assertThat(type)
                .isSameAs(int[].class);
    }

    @Test
    void resolvesMultiDimensionalObjectArrayTypeReference() {
        Expression expression = this.parser.parseExpression("T(java.lang.String[][])");

        Class<?> type = expression.getValue(Class.class);

        assertThat(type)
                .isSameAs(String[][].class);
    }
}
