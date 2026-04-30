/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;

import javax.el.ELContext;
import javax.el.ELProcessor;
import javax.el.ExpressionFactory;
import javax.el.MethodExpression;
import javax.el.ValueExpression;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ELProcessorTest {
    @BeforeAll
    static void configureExpressionFactory() {
        System.setProperty(
                ExpressionFactory.class.getName(),
                TestExpressionFactory.class.getName());
    }

    @Test
    void defineFunctionByMethodNameScansDeclaredMethods() throws Exception {
        ELProcessor processor = new ELProcessor();

        processor.defineFunction(
                "sample",
                "doubleValue",
                StaticFunctions.class.getName(),
                "twice");

        assertThat(processor.getELManager()
                .getELContext()
                .getFunctionMapper()
                .resolveFunction("sample", "doubleValue"))
                .isNotNull()
                .extracting(method -> method.getName())
                .isEqualTo("twice");
    }

    @Test
    void defineFunctionBySignatureResolvesClassAndArrayParameterTypes() throws Exception {
        ELProcessor processor = new ELProcessor();

        processor.defineFunction(
                "sample",
                "describe",
                StaticFunctions.class.getName(),
                "java.lang.String describe(java.lang.String,int[],java.lang.String[][])");

        assertThat(processor.getELManager()
                .getELContext()
                .getFunctionMapper()
                .resolveFunction("sample", "describe"))
                .isNotNull()
                .satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("describe");
                    assertThat(method.getParameterTypes())
                            .containsExactly(String.class, int[].class, String[][].class);
                });
    }

    public static final class StaticFunctions {
        public static int twice(int value) {
            return value * 2;
        }

        public static String describe(String label, int[] counts, String[][] names) {
            return label + ':' + counts.length + ':' + names.length;
        }
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
}
