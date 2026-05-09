/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_el_2_2_spec;

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

import static org.assertj.core.api.Assertions.assertThat;

public class BeanELResolverTest {

    @BeforeAll
    static void configureExpressionFactory() {
        System.setProperty("javax.el.ExpressionFactory", TestExpressionFactory.class.getName());
    }

    @Test
    void invokesBeanMethodWhenParameterTypesAreProvided() {
        BeanELResolver resolver = new BeanELResolver();

        Object value = resolver.invoke(
                newContext(),
                new MethodTarget(),
                "describeInput",
                new Class<?>[] {String.class},
                new Object[] {"typed"});

        assertThat(value).isEqualTo("input:typed");
    }

    @Test
    void invokesBeanMethodWhenParameterTypesAreInferredFromArguments() {
        BeanELResolver resolver = new BeanELResolver();

        Object value = resolver.invoke(
                newContext(),
                new MethodTarget(),
                "describeInput",
                null,
                new Object[] {"inferred"});

        assertThat(value).isEqualTo("input:inferred");
    }

    @Test
    void invokesVarargsMethodWithoutTrailingValues() {
        BeanELResolver resolver = new BeanELResolver();

        Object value = resolver.invoke(
                newContext(),
                new MethodTarget(),
                "joinValues",
                null,
                new Object[] {"prefix"});

        assertThat(value).isEqualTo("prefix:");
    }

    @Test
    void invokesVarargsMethodWithTrailingValues() {
        BeanELResolver resolver = new BeanELResolver();

        Object value = resolver.invoke(
                newContext(),
                new MethodTarget(),
                "joinValues",
                null,
                new Object[] {"prefix", "first", "second"});

        assertThat(value).isEqualTo("prefix:1,2");
    }

    @Test
    void readsAndWritesPropertyOnPackagePrivateBeanViaPublicInterfaceMethods() {
        BeanELResolver resolver = new BeanELResolver();
        InterfaceBackedBean bean = new InterfaceBackedBean("before");
        ELContext context = newContext();

        Object value = resolver.getValue(context, bean, "name");

        assertThat(value).isEqualTo("before");
        assertThat(context.isPropertyResolved()).isTrue();

        resolver.setValue(newContext(), bean, "name", "after");

        assertThat(bean.getName()).isEqualTo("after");
    }

    @Test
    void readsPropertyOnPackagePrivateBeanViaPublicSuperclassMethod() {
        BeanELResolver resolver = new BeanELResolver();
        InheritedNameBean bean = new InheritedNameBean("base");

        Object value = resolver.getValue(newContext(), bean, "name");

        assertThat(value).isEqualTo("base");
    }

    private static ELContext newContext() {
        return new TestELContext();
    }

    public interface NameContract {
        String getName();

        void setName(String value);
    }

    static final class InterfaceBackedBean implements NameContract {
        private String name;

        InterfaceBackedBean(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String value) {
            this.name = value;
        }
    }

    public static class PublicNameBase {
        private final String name;

        public PublicNameBase(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    static final class InheritedNameBean extends PublicNameBase {
        InheritedNameBean(String name) {
            super(name);
        }
    }

    public static final class MethodTarget {
        public String describeInput(String value) {
            return "input:" + value;
        }

        public String joinValues(String prefix, String... values) {
            return prefix + ":" + String.join(",", values);
        }
    }

    public static final class TestExpressionFactory extends ExpressionFactory {
        public TestExpressionFactory() {
        }

        @Override
        public Object coerceToType(Object obj, Class<?> expectedType) throws ELException {
            if (obj == null || expectedType.isInstance(obj)) {
                return obj;
            }
            if (expectedType == String.class) {
                return String.valueOf(obj);
            }
            throw new ELException("Unsupported coercion to " + expectedType.getName());
        }

        @Override
        public ValueExpression createValueExpression(ELContext context, String expression, Class<?> expectedType) {
            throw new UnsupportedOperationException("Value expressions are not used by these tests");
        }

        @Override
        public ValueExpression createValueExpression(Object instance, Class<?> expectedType) {
            throw new UnsupportedOperationException("Value expressions are not used by these tests");
        }

        @Override
        public MethodExpression createMethodExpression(
                ELContext context,
                String expression,
                Class<?> expectedReturnType,
                Class<?>[] expectedParamTypes) {
            throw new UnsupportedOperationException("Method expressions are not used by these tests");
        }
    }

    private static final class TestELContext extends ELContext {
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
