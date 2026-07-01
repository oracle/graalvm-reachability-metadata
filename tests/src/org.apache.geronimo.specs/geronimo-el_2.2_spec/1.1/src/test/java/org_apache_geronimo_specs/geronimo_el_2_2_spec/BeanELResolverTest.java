/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_el_2_2_spec;

import static org.assertj.core.api.Assertions.assertThat;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.FunctionMapper;
import javax.el.MethodExpression;
import javax.el.ValueExpression;
import javax.el.VariableMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class BeanELResolverTest {
    private static final String EXPRESSION_FACTORY_PROPERTY = "javax.el.ExpressionFactory";

    @BeforeAll
    static void configureExpressionFactory() {
        System.setProperty(EXPRESSION_FACTORY_PROPERTY, TestExpressionFactory.class.getName());
    }

    @Test
    void readsAndWritesPropertyDeclaredOnPublicInterface() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();
        InterfaceBackedBean bean = new InterfaceBackedBean();

        resolver.setValue(context, bean, "name", "created through setter");

        assertThat(resolver.getValue(context, bean, "name")).isEqualTo("created through setter");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void readsAndWritesPropertyDeclaredOnPublicSuperclass() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();
        SubclassPropertyBean bean = new SubclassPropertyBean();

        resolver.setValue(context, bean, "quantity", 7);

        assertThat(resolver.getValue(context, bean, "quantity")).isEqualTo(7);
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void invokesMethodDiscoveredFromPublicMethodsWhenParameterTypesAreUnknown() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();
        InvocationBean bean = new InvocationBean();

        Object result = resolver.invoke(context, bean, new StringBuilder("join"), null,
                new Object[] {"left", "right"});

        assertThat(result).isEqualTo("left:right");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void invokesMethodLookedUpFromExplicitParameterTypes() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();
        InvocationBean bean = new InvocationBean();

        Object result = resolver.invoke(context, bean, "repeat", new Class<?>[] {String.class, int.class},
                new Object[] {"ha", 3});

        assertThat(result).isEqualTo("hahaha");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void invokesVarargsMethodWithNoVarargsValues() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();
        InvocationBean bean = new InvocationBean();

        Object result = resolver.invoke(context, bean, "sum", null, new Object[] {"empty"});

        assertThat(result).isEqualTo("empty=0");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void invokesVarargsMethodWithCoercedVarargsArray() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();
        InvocationBean bean = new InvocationBean();

        Object result = resolver.invoke(context, bean, "sum", null, new Object[] {"values", "10", "20"});

        assertThat(result).isEqualTo("values=3");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    public interface NamedBean {
        String getName();

        void setName(String name);
    }

    static final class InterfaceBackedBean implements NamedBean {
        private String name;

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }
    }

    public static class PublicPropertyBean {
        private int quantity;

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    static final class SubclassPropertyBean extends PublicPropertyBean {
    }

    public static final class InvocationBean {
        public String join(String left, String right) {
            return left + ':' + right;
        }

        public String repeat(String value, int count) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < count; i++) {
                builder.append(value);
            }
            return builder.toString();
        }

        public String sum(String label, int... values) {
            int sum = 0;
            for (int value : values) {
                sum += value;
            }
            return label + '=' + sum;
        }
    }

    public static final class TestExpressionFactory extends ExpressionFactory {
        @Override
        public Object coerceToType(Object obj, Class<?> expectedType) throws ELException {
            if (obj == null) {
                return null;
            }
            if (expectedType == String.class) {
                return obj.toString();
            }
            if (expectedType == int.class || expectedType == Integer.class) {
                if (obj instanceof Number) {
                    return ((Number) obj).intValue();
                }
                return Integer.valueOf(obj.toString());
            }
            if (expectedType.isInstance(obj)) {
                return obj;
            }
            throw new ELException("Unsupported coercion to " + expectedType.getName());
        }

        @Override
        public ValueExpression createValueExpression(ELContext context, String expression, Class<?> expectedType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ValueExpression createValueExpression(Object instance, Class<?> expectedType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MethodExpression createMethodExpression(ELContext context, String expression,
                Class<?> expectedReturnType, Class<?>[] expectedParamTypes) {
            throw new UnsupportedOperationException();
        }
    }

    static final class SimpleELContext extends ELContext {
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
            return null;
        }
    }
}
