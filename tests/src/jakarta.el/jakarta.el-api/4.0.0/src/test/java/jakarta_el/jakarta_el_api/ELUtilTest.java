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

import jakarta.el.BeanELResolver;
import jakarta.el.ELClass;
import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.el.FunctionMapper;
import jakarta.el.MethodExpression;
import jakarta.el.PropertyNotFoundException;
import jakarta.el.StaticFieldELResolver;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ELUtilTest {
    private static final String EXPRESSION_FACTORY_PROPERTY = "jakarta.el.ExpressionFactory";
    private static String previousExpressionFactory;

    @BeforeAll
    static void installExpressionFactory() {
        previousExpressionFactory = System.getProperty(EXPRESSION_FACTORY_PROPERTY);
        System.setProperty(EXPRESSION_FACTORY_PROPERTY, SimpleExpressionFactory.class.getName());
    }

    @AfterAll
    static void restoreExpressionFactory() {
        if (previousExpressionFactory == null) {
            System.clearProperty(EXPRESSION_FACTORY_PROPERTY);
        } else {
            System.setProperty(EXPRESSION_FACTORY_PROPERTY, previousExpressionFactory);
        }
    }

    @Test
    void invokesPublicStaticVarargsMethod() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        SimpleELContext context = new SimpleELContext();

        Object result = resolver.invoke(
                context,
                new ELClass(Operations.class),
                "join",
                null,
                new Object[] {"letters", "a", "b" });

        assertThat(result).isEqualTo("letters:a,b");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void invokesPublicConstructorResolvedFromNonPublicSubclass() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        SimpleELContext context = new SimpleELContext();

        Object result = resolver.invoke(
                context,
                new ELClass(NonPublicConstructable.class),
                "<init>",
                new Class<?>[] {String.class },
                new Object[] {"created" });

        assertThat(result).isInstanceOf(PublicConstructable.class);
        assertThat(((PublicConstructable) result).getName()).isEqualTo("created");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void invokesPublicInterfaceMethodResolvedFromNonPublicBean() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();

        Object result = resolver.invoke(
                context,
                new NonPublicGreeter(),
                "greet",
                new Class<?>[] {String.class },
                new Object[] {"Ada" });

        assertThat(result).isEqualTo("Hello Ada");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void invokesPublicSuperclassMethodResolvedFromNonPublicBean() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();

        Object result = resolver.invoke(
                context,
                new NonPublicDerived(),
                "describe",
                new Class<?>[] {String.class },
                new Object[] {"base" });

        assertThat(result).isEqualTo("described base");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void loadsLocalizedMessageBundleForStaticFieldErrors() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();
        SimpleELContext context = new SimpleELContext();
        context.setLocale(Locale.ENGLISH);

        assertThatThrownBy(() -> resolver.getValue(context, new ELClass(Operations.class), "missingField"))
                .isInstanceOf(PropertyNotFoundException.class)
                .hasMessageContaining(Operations.class.getName())
                .hasMessageContaining("missingField");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    public static class SimpleExpressionFactory extends ExpressionFactory {
        @Override
        public ValueExpression createValueExpression(ELContext context, String expression, Class<?> expectedType) {
            throw new UnsupportedOperationException("Expression parsing is not needed by these tests");
        }

        @Override
        public ValueExpression createValueExpression(Object instance, Class<?> expectedType) {
            throw new UnsupportedOperationException("Expression parsing is not needed by these tests");
        }

        @Override
        public MethodExpression createMethodExpression(
                ELContext context,
                String expression,
                Class<?> expectedReturnType,
                Class<?>[] expectedParamTypes) {
            throw new UnsupportedOperationException("Expression parsing is not needed by these tests");
        }

        @Override
        public Object coerceToType(Object obj, Class<?> targetType) {
            return coerce(obj, targetType);
        }

        static Object coerce(Object obj, Class<?> targetType) {
            if (targetType == null) {
                throw new NullPointerException("targetType");
            }
            Class<?> boxedTargetType = box(targetType);
            if (obj == null) {
                return targetType.isPrimitive() ? primitiveDefault(targetType) : null;
            }
            if (boxedTargetType.isInstance(obj)) {
                return obj;
            }
            if (boxedTargetType == String.class) {
                return obj.toString();
            }
            throw new ELException("Cannot coerce " + obj + " to " + targetType.getName());
        }

        private static Class<?> box(Class<?> type) {
            if (!type.isPrimitive()) {
                return type;
            }
            if (type == boolean.class) {
                return Boolean.class;
            }
            if (type == char.class) {
                return Character.class;
            }
            if (type == byte.class) {
                return Byte.class;
            }
            if (type == short.class) {
                return Short.class;
            }
            if (type == int.class) {
                return Integer.class;
            }
            if (type == long.class) {
                return Long.class;
            }
            if (type == float.class) {
                return Float.class;
            }
            return Double.class;
        }

        private static Object primitiveDefault(Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            if (type == byte.class) {
                return (byte) 0;
            }
            if (type == short.class) {
                return (short) 0;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == float.class) {
                return 0.0F;
            }
            return 0.0D;
        }
    }

    public static class Operations {
        public static String join(String label, String... values) {
            return label + ":" + String.join(",", values);
        }
    }

    public static class PublicConstructable {
        private final String name;

        public PublicConstructable(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static class NonPublicConstructable extends PublicConstructable {
        public NonPublicConstructable(String name) {
            super(name);
        }
    }

    public interface GreetingContract {
        String greet(String name);
    }

    private static class NonPublicGreeter implements GreetingContract {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    public static class PublicBaseOperation {
        public String describe(String value) {
            return "described " + value;
        }
    }

    private static class NonPublicDerived extends PublicBaseOperation {
    }

    private static class SimpleELContext extends ELContext {
        private final ELResolver resolver = new CoercingELResolver();

        private SimpleELContext() {
            putContext(ExpressionFactory.class, new SimpleExpressionFactory());
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

    private static class CoercingELResolver extends ELResolver {
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
            return null;
        }

        @Override
        public Object convertToType(ELContext context, Object obj, Class<?> targetType) {
            context.setPropertyResolved(true);
            return SimpleExpressionFactory.coerce(obj, targetType);
        }
    }
}
