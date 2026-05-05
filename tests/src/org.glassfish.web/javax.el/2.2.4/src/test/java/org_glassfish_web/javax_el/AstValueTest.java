/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_web.javax_el;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.FunctionMapper;
import javax.el.MethodExpression;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

import org.junit.jupiter.api.Test;

import com.sun.el.ExpressionFactoryImpl;

public class AstValueTest {
    @Test
    void methodExpressionWithoutInlineArgumentsInvokesTargetMethod() {
        ExpressionFactory expressionFactory = new ExpressionFactoryImpl();
        SimpleELContext context = new SimpleELContext();
        MethodTarget target = new MethodTarget("prefix");
        context.setVariable("target", expressionFactory.createValueExpression(target, MethodTarget.class));
        MethodExpression expression = expressionFactory.createMethodExpression(
                context,
                "${target.format}",
                String.class,
                new Class<?>[] {String.class, int.class});

        Object value = expression.invoke(context, new Object[] {"item", 7});

        assertThat(value).isEqualTo("prefix:item:7");
    }

    public static final class MethodTarget {
        private final String prefix;

        public MethodTarget(String prefix) {
            this.prefix = prefix;
        }

        public String format(String name, int count) {
            return prefix + ":" + name + ":" + count;
        }
    }

    private static final class SimpleELContext extends ELContext {
        private final MapVariableMapper variableMapper = new MapVariableMapper();

        private void setVariable(String name, ValueExpression expression) {
            variableMapper.setVariable(name, expression);
        }

        @Override
        public ELResolver getELResolver() {
            return null;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return null;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return variableMapper;
        }
    }

    private static final class MapVariableMapper extends VariableMapper {
        private final Map<String, ValueExpression> expressions = new HashMap<>();

        @Override
        public ValueExpression resolveVariable(String variable) {
            return expressions.get(variable);
        }

        @Override
        public ValueExpression setVariable(String variable, ValueExpression expression) {
            return expressions.put(variable, expression);
        }
    }
}
