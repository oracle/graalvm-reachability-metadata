/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Objects;
import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELProcessor;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ELProcessorTest {

    @BeforeAll
    static void configureExpressionFactory() {
        System.setProperty("javax.el.ExpressionFactory", MinimalExpressionFactory.class.getName());
    }

    @Test
    void defineFunctionByNameScansDeclaredMethods() throws Exception {
        ELProcessor processor = newProcessor();

        processor.defineFunction("math", "sum", FunctionLibrary.class.getName(), "sum");

        assertMappedFunction(processor, "math", "sum", "sum");
    }

    @Test
    void defineFunctionWithObjectSignatureLoadsParameterClasses() throws Exception {
        ELProcessor processor = newProcessor();

        processor.defineFunction(
                "text",
                "join",
                FunctionLibrary.class.getName(),
                "java.lang.String join(java.lang.String,java.lang.String)");

        assertMappedFunction(processor, "text", "join", "join");
    }

    @Test
    void defineFunctionWithPrimitiveArraySignatureResolvesArrayType() throws Exception {
        ELProcessor processor = newProcessor();

        processor.defineFunction("arrays", "sum", FunctionLibrary.class.getName(), "int sumArray(int[])");

        assertMappedFunction(processor, "arrays", "sum", "sumArray");
    }

    @Test
    void defineFunctionWithMultiDimensionalReferenceArraySignatureResolvesArrayType() throws Exception {
        ELProcessor processor = newProcessor();

        processor.defineFunction(
                "arrays",
                "cellCount",
                FunctionLibrary.class.getName(),
                "int countCells(java.lang.String[][])");

        assertMappedFunction(processor, "arrays", "cellCount", "countCells");
    }

    private static ELProcessor newProcessor() {
        configureExpressionFactory();
        return new ELProcessor();
    }

    private static void assertMappedFunction(
            ELProcessor processor, String prefix, String functionName, String expectedMethodName) {
        Method mappedMethod = processor.getELManager()
                .getELContext()
                .getFunctionMapper()
                .resolveFunction(prefix, functionName);

        assertThat(mappedMethod).isNotNull();
        assertThat(mappedMethod.getName()).isEqualTo(expectedMethodName);
    }

    public static final class FunctionLibrary {

        public static int sum(int left, int right) {
            return left + right;
        }

        public static String join(String left, String right) {
            return left + right;
        }

        public static int sumArray(int[] values) {
            int total = 0;
            for (int value : values) {
                total += value;
            }
            return total;
        }

        public static int countCells(String[][] values) {
            int count = 0;
            for (String[] row : values) {
                count += row.length;
            }
            return count;
        }
    }

    public static final class MinimalExpressionFactory extends ExpressionFactory {

        @Override
        public ValueExpression createValueExpression(ELContext context, String expression, Class<?> expectedType) {
            throw new UnsupportedOperationException("Expression parsing is not used by these tests");
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

        @Override
        public Object coerceToType(Object obj, Class<?> targetType) {
            if (obj == null || targetType.isInstance(obj)) {
                return obj;
            }
            if (targetType == String.class) {
                return Objects.toString(obj);
            }
            throw new ELException("Unsupported conversion to " + targetType.getName());
        }

        @Override
        public ELResolver getStreamELResolver() {
            return null;
        }
    }
}
