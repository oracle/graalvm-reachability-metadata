/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_web.javax_el;

import java.beans.FeatureDescriptor;
import java.util.Iterator;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.MethodExpression;
import javax.el.VariableMapper;

import com.sun.el.ExpressionFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AstValueTest {

    @Test
    void invokesMethodExpressionWithParametersSuppliedAtInvocationTime() {
        ELContext context = new SingleBeanELContext("text", "reflection-target");
        ExpressionFactoryImpl expressionFactory = new ExpressionFactoryImpl();
        MethodExpression expression = expressionFactory.createMethodExpression(
                context,
                "#{text.substring}",
                String.class,
                new Class<?>[] { int.class, int.class });

        Object value = expression.invoke(context, new Object[] { 0, 10 });

        assertThat(expression.isParmetersProvided()).isFalse();
        assertThat(value).isEqualTo("reflection");
    }

    static final class SingleBeanELContext extends ELContext {
        private final ELResolver resolver;

        SingleBeanELContext(String name, Object value) {
            this.resolver = new SingleBeanELResolver(name, value);
        }

        @Override
        public ELResolver getELResolver() {
            return resolver;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return null;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return null;
        }
    }

    static final class SingleBeanELResolver extends ELResolver {
        private final String name;
        private final Object value;

        SingleBeanELResolver(String name, Object value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            if (base == null && name.equals(property)) {
                context.setPropertyResolved(true);
                return value;
            }
            return null;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            if (base == null && name.equals(property)) {
                context.setPropertyResolved(true);
                return value.getClass();
            }
            return null;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object newValue) {
            if (base == null && name.equals(property)) {
                context.setPropertyResolved(true);
            }
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            if (base == null && name.equals(property)) {
                context.setPropertyResolved(true);
            }
            return true;
        }

        @Override
        public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
            return null;
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return Object.class;
        }
    }
}
