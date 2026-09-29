/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.MethodExpression;

import org.apache.el.ExpressionFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AstValueTest {

    @Test
    void invokesVarargsMethodExpressionWithArgumentsDeclaredInExpression() {
        ELManager manager = new ELManager();
        ELContext context = manager.getELContext();
        ExpressionFactoryImpl expressionFactory = new ExpressionFactoryImpl();
        manager.defineBean("formatter", new VarargsTarget());

        MethodExpression expression = expressionFactory.createMethodExpression(
                context,
                "#{formatter.joinValues('prefix', 'alpha', 'beta')}",
                String.class,
                null);

        Object value = expression.invoke(context, null);

        assertThat(expression.isParametersProvided()).isTrue();
        assertThat(value).isEqualTo("prefix[alpha,beta]");
    }

    public static final class VarargsTarget {
        public String joinValues(String prefix, String... values) {
            return prefix + "[" + String.join(",", values) + "]";
        }
    }
}
