/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_web.javax_el;

import java.lang.reflect.Method;

import javax.el.CompositeELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

import com.sun.el.ExpressionFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AstFunctionTest {

    @Test
    void invokesMappedStaticFunctionWhenValueExpressionIsEvaluated() throws Exception {
        Method functionMethod = AstFunctionTest.class.getMethod("formatName", String.class, String.class);
        ELContext context = new FunctionBackedELContext("names", "format", functionMethod);
        ExpressionFactoryImpl expressionFactory = new ExpressionFactoryImpl();

        ValueExpression expression = expressionFactory.createValueExpression(
                context, "${names:format('Ada', 'Lovelace')}", String.class);
        Object value = expression.getValue(context);

        assertThat(value).isEqualTo("Lovelace, Ada");
    }

    public static String formatName(String firstName, String lastName) {
        return lastName + ", " + firstName;
    }

    static final class FunctionBackedELContext extends ELContext {
        private final ELResolver resolver = new CompositeELResolver();
        private final FunctionMapper functionMapper;

        FunctionBackedELContext(String prefix, String localName, Method method) {
            this.functionMapper = new SingleFunctionMapper(prefix, localName, method);
        }

        @Override
        public ELResolver getELResolver() {
            return resolver;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return functionMapper;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return null;
        }
    }

    static final class SingleFunctionMapper extends FunctionMapper {
        private final String prefix;
        private final String localName;
        private final Method method;

        SingleFunctionMapper(String prefix, String localName, Method method) {
            this.prefix = prefix;
            this.localName = localName;
            this.method = method;
        }

        @Override
        public Method resolveFunction(String requestedPrefix, String requestedLocalName) {
            if (prefix.equals(requestedPrefix) && localName.equals(requestedLocalName)) {
                return method;
            }
            return null;
        }
    }
}
