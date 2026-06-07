/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_expression;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelCompiler;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import static org.assertj.core.api.Assertions.assertThat;

public class SpelCompilerTest {
    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Test
    void compilesEvaluatedArithmeticExpressionAndEvaluatesCompiledForm() {
        Expression expression = this.parser.parseExpression("1 + 2");

        assertThat(expression.getValue(Integer.class))
                .isEqualTo(3);
        try {
            assertThat(SpelCompiler.compile(expression))
                    .isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        }

        assertThat(expression.getValue(Integer.class))
                .isEqualTo(3);
    }
}
