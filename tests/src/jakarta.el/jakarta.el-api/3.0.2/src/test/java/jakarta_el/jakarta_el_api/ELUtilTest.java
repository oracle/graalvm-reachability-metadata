/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.beans.FeatureDescriptor;
import java.util.Iterator;
import java.util.Locale;

import javax.el.BeanELResolver;
import javax.el.ELClass;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.FunctionMapper;
import javax.el.MethodExpression;
import javax.el.PropertyNotFoundException;
import javax.el.StaticFieldELResolver;
import javax.el.VariableMapper;
import javax.el.ValueExpression;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ELUtilTest {
    @BeforeAll
    static void configureExpressionFactory() {
        System.setProperty(
                ExpressionFactory.class.getName(),
                TestExpressionFactory.class.getName());
    }

    @Test
    void staticFieldResolverInvokesPublicStaticVarargsMethod() {
        TestELContext context = new TestELContext();
        StaticFieldELResolver resolver = new StaticFieldELResolver();

        Object value = resolver.invoke(
                context,
                new ELClass(StaticOperations.class),
                "join",
                null,
                new Object[] { "prefix", "one", "two" });

        assertThat(value).isEqualTo("prefix:one,two");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void staticFieldResolverInvokesConstructorResolvedFromPublicSuperclass() {
        TestELContext context = new TestELContext();
        StaticFieldELResolver resolver = new StaticFieldELResolver();

        Object value = resolver.invoke(
                context,
                new ELClass(HiddenConstructedChild.class),
                "<init>",
                new Class<?>[] { String.class },
                new Object[] { "created" });

        assertThat(value).isInstanceOf(PublicStringConstructed.class);
        assertThat(((PublicStringConstructed) value).label()).isEqualTo("created");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void beanResolverInvokesMethodResolvedFromPublicInterface() {
        TestELContext context = new TestELContext();
        BeanELResolver resolver = new BeanELResolver();

        Object value = resolver.invoke(
                context,
                new HiddenInterfaceGreeting(),
                "interfaceGreeting",
                new Class<?>[] { String.class },
                new Object[] { "hello" });

        assertThat(value).isEqualTo("hello from interface");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void beanResolverInvokesMethodResolvedFromPublicSuperclass() {
        TestELContext context = new TestELContext();
        BeanELResolver resolver = new BeanELResolver();

        Object value = resolver.invoke(
                context,
                new HiddenInheritedGreeting(),
                "inheritedGreeting",
                new Class<?>[] { String.class },
                new Object[] { "hello" });

        assertThat(value).isEqualTo("hello from superclass");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void staticFieldResolverUsesLocalizedMessageForMissingField() {
        TestELContext context = new TestELContext();
        context.setLocale(Locale.ENGLISH);
        StaticFieldELResolver resolver = new StaticFieldELResolver();

        assertThatThrownBy(() -> resolver.getValue(
                context,
                new ELClass(StaticOperations.class),
                "DOES_NOT_EXIST"))
                .isInstanceOf(PropertyNotFoundException.class)
                .hasMessageContaining(StaticOperations.class.getName())
                .hasMessageContaining("DOES_NOT_EXIST");
    }

    public static final class StaticOperations {
        public static String join(String prefix, String... values) {
            return prefix + ":" + String.join(",", values);
        }
    }

    public static class PublicStringConstructed {
        private final String label;

        public PublicStringConstructed(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    static final class HiddenConstructedChild extends PublicStringConstructed {
        public HiddenConstructedChild(String label) {
            super(label);
        }
    }

    public interface InterfaceGreeting {
        String interfaceGreeting(String prefix);
    }

    static final class HiddenInterfaceGreeting implements InterfaceGreeting {
        @Override
        public String interfaceGreeting(String prefix) {
            return prefix + " from interface";
        }
    }

    public static class PublicGreetingBase {
        public String inheritedGreeting(String prefix) {
            return prefix + " from superclass";
        }
    }

    static final class HiddenInheritedGreeting extends PublicGreetingBase {
    }

    public static final class TestExpressionFactory extends ExpressionFactory {
        @Override
        public ValueExpression createValueExpression(
                ELContext context,
                String expression,
                Class<?> expectedType) {
            throw new UnsupportedOperationException(
                    "Expression parsing is not used by these tests");
        }

        @Override
        public ValueExpression createValueExpression(Object instance, Class<?> expectedType) {
            throw new UnsupportedOperationException(
                    "Value expressions are not used by these tests");
        }

        @Override
        public MethodExpression createMethodExpression(
                ELContext context,
                String expression,
                Class<?> expectedReturnType,
                Class<?>[] expectedParamTypes) {
            throw new UnsupportedOperationException(
                    "Method expressions are not used by these tests");
        }

        @Override
        public Object coerceToType(Object object, Class<?> targetType) {
            return object;
        }
    }

    private static final class TestELContext extends ELContext {
        private final ELResolver resolver = new PassthroughTypeResolver();

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

    private static final class PassthroughTypeResolver extends ELResolver {
        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            return null;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            return null;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
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

        @Override
        public Object convertToType(ELContext context, Object object, Class<?> targetType) {
            context.setPropertyResolved(true);
            return object;
        }
    }
}
