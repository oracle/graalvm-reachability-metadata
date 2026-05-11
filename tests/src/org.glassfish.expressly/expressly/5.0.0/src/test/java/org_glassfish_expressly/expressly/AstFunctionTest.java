/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_expressly.expressly;

import java.lang.reflect.Method;

import org.glassfish.expressly.ExpressionFactoryImpl;
import org.junit.jupiter.api.Test;

import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;

import static org.assertj.core.api.Assertions.assertThat;

public class AstFunctionTest {

    @Test
    void invokesMappedStaticFunctionWithCoercedArguments() throws Exception {
        ExpressionFactory expressionFactory = new ExpressionFactoryImpl();
        StandardELContext context = new StandardELContext(expressionFactory);
        Method function = AstFunctionTest.class.getMethod("formatScore", String.class, int.class);
        context.getFunctionMapper().mapFunction("score", "format", function);
        ValueExpression expression = expressionFactory.createValueExpression(context, "${score:format('team', '7')}", String.class);

        Object value = expression.getValue(context);

        assertThat(value).isEqualTo("team:7");
    }

    public static String formatScore(String name, int score) {
        return name + ":" + score;
    }
}
