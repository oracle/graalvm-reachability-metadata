/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.ELProcessor;
import javax.el.ValueExpression;

import org.apache.el.ExpressionFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AstFunctionTest {

    @Test
    void invokesMappedStaticFunctionDuringExpressionEvaluation()
            throws ClassNotFoundException, NoSuchMethodException {
        ELProcessor processor = new ELProcessor();
        processor.defineFunction(
                "lib",
                "repeatBySignature",
                FunctionLibrary.class.getName(),
                "java.lang.String repeat(java.lang.String,int)");

        ELManager manager = processor.getELManager();
        ELContext context = manager.getELContext();
        ExpressionFactoryImpl expressionFactory = new ExpressionFactoryImpl();
        ValueExpression expression = expressionFactory.createValueExpression(
                context,
                "${lib:repeatBySignature('ha', 3)}",
                Object.class);

        Object value = expression.getValue(context);

        assertThat(value).isEqualTo("hahaha");
    }

    public static final class FunctionLibrary {
        private FunctionLibrary() {
        }

        public static String repeat(String value, int count) {
            return value.repeat(count);
        }

        public static String repeat(String value) {
            return value;
        }
    }
}
