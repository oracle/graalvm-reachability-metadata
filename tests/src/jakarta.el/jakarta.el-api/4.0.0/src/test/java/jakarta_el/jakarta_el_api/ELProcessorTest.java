/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ELProcessor;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.el.MethodExpression;
import jakarta.el.ValueExpression;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ELProcessorTest {
    private static final String EXPRESSION_FACTORY_PROPERTY = "jakarta.el.ExpressionFactory";
    private static String previousExpressionFactory;

    @BeforeAll
    static void installExpressionFactory() {
        previousExpressionFactory = System.getProperty(EXPRESSION_FACTORY_PROPERTY);
        System.setProperty(EXPRESSION_FACTORY_PROPERTY, ProcessorExpressionFactory.class.getName());
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
    void definesFunctionByScanningDeclaredMethods() throws Exception {
        ELProcessor processor = new ELProcessor();

        processor.defineFunction("fn", "triple", ELProcessorFunctionLibrary.class.getName(), "triple");

        Method mappedMethod = resolveFunction(processor, "triple");
        assertThat(mappedMethod).isNotNull();
        assertThat(mappedMethod.getDeclaringClass()).isEqualTo(ELProcessorFunctionLibrary.class);
        assertThat(mappedMethod.getName()).isEqualTo("triple");
    }

    @Test
    void definesFunctionFromSignatureWithObjectAndArrayParameterTypes() throws Exception {
        ELProcessor processor = new ELProcessor();

        processor.defineFunction(
                "fn",
                "describe",
                ELProcessorFunctionLibrary.class.getName(),
                "java.lang.String describe(java.lang.String,java.lang.String[],java.lang.String[][],int)");

        Method mappedMethod = resolveFunction(processor, "describe");
        assertThat(mappedMethod).isNotNull();
        assertThat(mappedMethod.getName()).isEqualTo("describe");
        assertThat(mappedMethod.getParameterTypes())
                .containsExactly(String.class, String[].class, String[][].class, int.class);
    }

    private static Method resolveFunction(ELProcessor processor, String function) {
        return processor.getELManager().getELContext().getFunctionMapper().resolveFunction("fn", function);
    }

    public static class ProcessorExpressionFactory extends ExpressionFactory {
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
            if (obj == null || targetType.isInstance(obj) || targetType == Object.class) {
                return obj;
            }
            throw new ELException("Cannot coerce " + obj + " to " + targetType.getName());
        }

        @Override
        public ELResolver getStreamELResolver() {
            return null;
        }
    }
}

final class ELProcessorFunctionLibrary {
    private ELProcessorFunctionLibrary() {
    }

    public static int triple(int value) {
        return value * 3;
    }

    public static String describe(String value, String[] labels, String[][] matrix, int count) {
        return value + ':' + labels.length + ':' + matrix.length + ':' + count;
    }
}
