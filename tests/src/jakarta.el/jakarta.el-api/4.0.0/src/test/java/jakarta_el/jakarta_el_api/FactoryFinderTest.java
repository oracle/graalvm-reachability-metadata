/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FactoryFinderTest {
    private static final String EXPRESSION_FACTORY_PROPERTY = "javax.el.ExpressionFactory";

    private ClassLoader originalContextClassLoader;
    private String originalExpressionFactoryProperty;

    @BeforeEach
    void captureThreadState() {
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        originalExpressionFactoryProperty = System.getProperty(EXPRESSION_FACTORY_PROPERTY);
    }

    @AfterEach
    void restoreThreadState() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        if (originalExpressionFactoryProperty == null) {
            System.clearProperty(EXPRESSION_FACTORY_PROPERTY);
        } else {
            System.setProperty(EXPRESSION_FACTORY_PROPERTY, originalExpressionFactoryProperty);
        }
    }

    @Test
    void looksUpServiceWithNullContextClassLoaderAndUsesPropertiesConstructor() {
        Thread.currentThread().setContextClassLoader(null);
        System.clearProperty(EXPRESSION_FACTORY_PROPERTY);
        assertThat(Thread.currentThread().getContextClassLoader()).isNull();

        Properties properties = new Properties();
        properties.setProperty("javax.el.cacheSize", "32");

        ExpressionFactory expressionFactory = ExpressionFactory.newInstance(properties);

        assertThat(expressionFactory).isInstanceOf(ServiceExpressionFactory.class);
        ServiceExpressionFactory serviceExpressionFactory = (ServiceExpressionFactory) expressionFactory;
        assertThat(serviceExpressionFactory.getConstructorMode()).isEqualTo("properties");
        assertThat(serviceExpressionFactory.getProperties()).isSameAs(properties);
        assertThat(serviceExpressionFactory.getProperties()).containsEntry("javax.el.cacheSize", "32");
    }

    public static final class ServiceExpressionFactory extends ExpressionFactory {
        private final String constructorMode;
        private final Properties properties;

        public ServiceExpressionFactory() {
            this.constructorMode = "default";
            this.properties = null;
        }

        public ServiceExpressionFactory(Properties properties) {
            this.constructorMode = "properties";
            this.properties = properties;
        }

        String getConstructorMode() {
            return constructorMode;
        }

        Properties getProperties() {
            return properties;
        }

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

        @Override
        public ELResolver getStreamELResolver() {
            return null;
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
}
