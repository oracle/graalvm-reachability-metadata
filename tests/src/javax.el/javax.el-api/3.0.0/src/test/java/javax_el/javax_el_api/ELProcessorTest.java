/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.javax_el_api;

import java.lang.reflect.Method;

import javax.el.ELContext;
import javax.el.ELProcessor;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ELProcessorTest {

    private static final String EXPRESSION_FACTORY_PROPERTY = ExpressionFactory.class.getName();

    private ClassLoader originalContextClassLoader;

    private String originalProvider;

    @BeforeEach
    void setUpExpressionFactoryProvider() {
        this.originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        this.originalProvider = System.getProperty(EXPRESSION_FACTORY_PROPERTY);

        Thread.currentThread().setContextClassLoader(ELProcessorTest.class.getClassLoader());
        System.setProperty(EXPRESSION_FACTORY_PROPERTY, StubExpressionFactory.class.getName());
    }

    @AfterEach
    void restoreExpressionFactoryProvider() {
        if (this.originalProvider == null) {
            System.clearProperty(EXPRESSION_FACTORY_PROPERTY);
        } else {
            System.setProperty(EXPRESSION_FACTORY_PROPERTY, this.originalProvider);
        }
        Thread.currentThread().setContextClassLoader(this.originalContextClassLoader);
    }

    @Test
    void defineFunctionResolvesMethodByNameFromDeclaredMethods() throws Exception {
        ELProcessor processor = new ELProcessor();

        processor.defineFunction("math", "increment", FunctionLibrary.class.getName(), "increment");

        Method resolvedMethod = processor.getELManager()
                .getELContext()
                .getFunctionMapper()
                .resolveFunction("math", "increment");

        assertThat(resolvedMethod).isNotNull();
        assertThat(resolvedMethod.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(resolvedMethod.getName()).isEqualTo("increment");
        assertThat(resolvedMethod.getParameterTypes()).containsExactly(int.class);
    }

    @Test
    void defineFunctionResolvesSingleDimensionArrayParameterTypes() throws Exception {
        ELProcessor processor = new ELProcessor();

        processor.defineFunction(
                "text",
                "join",
                FunctionLibrary.class.getName(),
                "java.lang.String join(java.lang.String[])"
        );

        Method resolvedMethod = processor.getELManager()
                .getELContext()
                .getFunctionMapper()
                .resolveFunction("text", "join");

        assertThat(resolvedMethod).isNotNull();
        assertThat(resolvedMethod.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(resolvedMethod.getName()).isEqualTo("join");
        assertThat(resolvedMethod.getParameterTypes()).containsExactly(String[].class);
    }

    @Test
    void defineFunctionResolvesMultiDimensionArrayParameterTypes() throws Exception {
        ELProcessor processor = new ELProcessor();

        processor.defineFunction(
                "text",
                "flatten",
                FunctionLibrary.class.getName(),
                "java.lang.String flatten(java.lang.String[][])"
        );

        Method resolvedMethod = processor.getELManager()
                .getELContext()
                .getFunctionMapper()
                .resolveFunction("text", "flatten");

        assertThat(resolvedMethod).isNotNull();
        assertThat(resolvedMethod.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(resolvedMethod.getName()).isEqualTo("flatten");
        assertThat(resolvedMethod.getParameterTypes()).containsExactly(String[][].class);
    }

    public static final class StubExpressionFactory extends ExpressionFactory {

        @Override
        public ValueExpression createValueExpression(ELContext context, String expression, Class<?> expectedType) {
            return null;
        }

        @Override
        public ValueExpression createValueExpression(Object instance, Class<?> expectedType) {
            return null;
        }

        @Override
        public MethodExpression createMethodExpression(
                ELContext context, String expression, Class<?> expectedReturnType, Class<?>[] expectedParamTypes) {
            return null;
        }

        @Override
        public Object coerceToType(Object obj, Class<?> targetType) {
            return obj;
        }
    }

    public static final class FunctionLibrary {

        public static int increment(int value) {
            return value + 1;
        }

        public static String join(String[] values) {
            return String.join(",", values);
        }

        public static String flatten(String[][] values) {
            return values.length == 0 ? "" : String.join(",", values[0]);
        }
    }
}
