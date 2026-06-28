/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_el.commons_el;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.FunctionMapper;
import javax.servlet.jsp.el.VariableResolver;
import org.apache.commons.el.Coercions;
import org.apache.commons.el.ExpressionEvaluatorImpl;
import org.apache.commons.el.Logger;
import org.junit.jupiter.api.Test;

public class PrimitiveObjectsTest {
    @Test
    public void resolvesPrimitiveWrapperClassThroughFunctionInvocation() throws Exception {
        ExpressionEvaluatorImpl evaluator = new ExpressionEvaluatorImpl(true);

        Object result = evaluator.evaluate(
                "${primitives:wrapper(type)}",
                Object.class,
                new PrimitiveTypeVariableResolver(),
                new PrimitiveObjectClassFunctionMapper());

        assertThat(result).isSameAs(Boolean.class);
    }

    @Test
    public void coercesValuesToPrimitiveWrapperObjectsThroughPublicCoercions() throws Exception {
        Logger logger = newDiscardingLogger();

        Object integerResult = Coercions.coerce(Character.valueOf('A'), Integer.TYPE, logger);
        Object longResult = Coercions.coerce(Integer.valueOf(42), Long.TYPE, logger);
        Object characterResult = Coercions.coerce(Integer.valueOf(65), Character.TYPE, logger);

        assertThat(integerResult).isEqualTo(Integer.valueOf(65));
        assertThat(longResult).isEqualTo(Long.valueOf(42L));
        assertThat(characterResult).isEqualTo(Character.valueOf('A'));
    }

    private static Logger newDiscardingLogger() {
        return new Logger(new PrintStream(OutputStream.nullOutputStream()));
    }

    private static final class PrimitiveObjectClassFunctionMapper implements FunctionMapper {
        private final Method getPrimitiveObjectClassMethod;

        private PrimitiveObjectClassFunctionMapper()
                throws ClassNotFoundException, NoSuchMethodException {
            Class<?> primitiveObjectsClass = Class.forName("org.apache.commons.el.PrimitiveObjects");
            getPrimitiveObjectClassMethod = primitiveObjectsClass.getMethod(
                    "getPrimitiveObjectClass", Class.class);
        }

        @Override
        public Method resolveFunction(String prefix, String localName) {
            if ("primitives".equals(prefix) && "wrapper".equals(localName)) {
                return getPrimitiveObjectClassMethod;
            }
            return null;
        }
    }

    private static final class PrimitiveTypeVariableResolver implements VariableResolver {
        @Override
        public Object resolveVariable(String name) throws ELException {
            if ("type".equals(name)) {
                return Boolean.TYPE;
            }
            throw new ELException("Unexpected variable: " + name);
        }
    }
}
