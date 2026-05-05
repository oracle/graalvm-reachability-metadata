/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_web.javax_el;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

import org.junit.jupiter.api.Test;

import com.sun.el.ExpressionFactoryImpl;
import com.sun.el.lang.FunctionMapperImpl;

public class AstFunctionTest {
    @Test
    void invokesMappedStaticFunctionDuringExpressionEvaluation() throws NoSuchMethodException {
        FunctionMapperImpl functionMapper = new FunctionMapperImpl();
        Method repeatMethod = FunctionLibrary.class.getMethod("repeat", String.class, int.class);
        functionMapper.addFunction("lib", "repeat", repeatMethod);
        ELContext context = new FunctionOnlyELContext(functionMapper);
        ExpressionFactoryImpl expressionFactory = new ExpressionFactoryImpl();
        ValueExpression expression = expressionFactory.createValueExpression(
                context,
                "${lib:repeat('ha', 3)}",
                Object.class);

        Object value = expression.getValue(context);

        assertThat(value).isEqualTo("hahaha");
    }

    public static final class FunctionLibrary {
        private FunctionLibrary() {
        }

        public static String repeat(String value, int count) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < count; i++) {
                result.append(value);
            }
            return result.toString();
        }
    }

    private static final class FunctionOnlyELContext extends ELContext {
        private final FunctionMapper functionMapper;

        private FunctionOnlyELContext(FunctionMapper functionMapper) {
            this.functionMapper = functionMapper;
        }

        @Override
        public ELResolver getELResolver() {
            return null;
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
}
