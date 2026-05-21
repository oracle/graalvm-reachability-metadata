/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_expression;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionReferenceTest {
    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Test
    void invokesRegisteredStaticMethodFunction() throws Exception {
        Method method = FunctionReferenceTest.class.getDeclaredMethod("formatGreeting", String.class, int.class);
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.registerFunction("formatGreeting", method);
        Expression expression = this.parser.parseExpression("#formatGreeting('Spring', 3)");

        String value = expression.getValue(context, String.class);

        assertThat(value)
                .isEqualTo("Hello Spring Hello Spring Hello Spring");
    }

    public static String formatGreeting(String name, int repetitions) {
        return ("Hello " + name + " ").repeat(repetitions).trim();
    }
}
