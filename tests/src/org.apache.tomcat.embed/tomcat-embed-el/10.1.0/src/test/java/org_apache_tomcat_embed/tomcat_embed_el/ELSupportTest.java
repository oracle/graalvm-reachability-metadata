/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import java.util.function.Function;

import jakarta.el.ELContext;
import jakarta.el.ELManager;
import jakarta.el.MethodExpression;

import org.apache.el.ExpressionFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ELSupportTest {

    @Test
    void coercesStringArrayToIntegerArray() {
        ExpressionFactoryImpl expressionFactory = new ExpressionFactoryImpl();

        Object value = expressionFactory.coerceToType(new String[] {"1", "2", "3"}, Integer[].class);

        assertThat(value).isInstanceOf(Integer[].class);
        assertThat((Integer[]) value).containsExactly(1, 2, 3);
    }

    @Test
    void coercesElLambdaToFunctionalInterface() {
        ELManager manager = new ELManager();
        ELContext context = manager.getELContext();
        ExpressionFactoryImpl expressionFactory = new ExpressionFactoryImpl();
        manager.defineBean("functions", new FunctionTarget());
        MethodExpression expression = expressionFactory.createMethodExpression(
                context,
                "#{functions.apply(input -> input, 'Tomcat')}",
                String.class,
                null);

        Object value = expression.invoke(context, null);

        assertThat(value).isEqualTo("Tomcat");
    }

    public static final class FunctionTarget {
        public String apply(Function<String, String> function, String value) {
            return function.apply(value);
        }
    }
}
