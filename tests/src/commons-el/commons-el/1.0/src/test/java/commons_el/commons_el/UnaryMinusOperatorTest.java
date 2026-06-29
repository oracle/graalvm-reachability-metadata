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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.VariableResolver;
import org.apache.commons.el.ExpressionEvaluatorImpl;
import org.apache.commons.el.Logger;
import org.apache.commons.el.UnaryMinusOperator;
import org.junit.jupiter.api.Test;

public class UnaryMinusOperatorTest {
    @Test
    public void appliesUnaryMinusToIntegralStringOperand() throws Exception {
        Logger logger = newDiscardingLogger();

        Object result = UnaryMinusOperator.SINGLETON.apply("42", logger);

        assertThat(result).isInstanceOf(Long.class);
        assertThat(result).isEqualTo(Long.valueOf(-42L));
    }

    @Test
    public void appliesUnaryMinusToFloatingPointStringOperand() throws Exception {
        Logger logger = newDiscardingLogger();

        Object result = UnaryMinusOperator.SINGLETON.apply("12.5", logger);

        assertThat(result).isInstanceOf(Double.class);
        assertThat(result).isEqualTo(Double.valueOf(-12.5D));
    }

    @Test
    public void appliesUnaryMinusToBigNumberOperands() throws Exception {
        Logger logger = newDiscardingLogger();

        Object integerResult = UnaryMinusOperator.SINGLETON.apply(new BigInteger("123"), logger);
        Object decimalResult = UnaryMinusOperator.SINGLETON.apply(new BigDecimal("123.45"), logger);

        assertThat(integerResult).isEqualTo(new BigInteger("-123"));
        assertThat(decimalResult).isEqualTo(new BigDecimal("-123.45"));
    }

    @Test
    public void evaluatesUnaryMinusForResolvedStringThroughExpressionEvaluator() throws Exception {
        ExpressionEvaluatorImpl evaluator = new ExpressionEvaluatorImpl(true);
        VariableResolver resolver = new MapBackedVariableResolver(Map.of("amount", "12.5"));

        Object result = evaluator.evaluate("${-amount}", Object.class, resolver, null);

        assertThat(result).isInstanceOf(Double.class);
        assertThat(result).isEqualTo(Double.valueOf(-12.5D));
    }

    private static Logger newDiscardingLogger() {
        return new Logger(new PrintStream(OutputStream.nullOutputStream()));
    }

    private static final class MapBackedVariableResolver implements VariableResolver {
        private final Map<String, Object> variables;

        private MapBackedVariableResolver(Map<String, Object> variables) {
            this.variables = variables;
        }

        @Override
        public Object resolveVariable(String name) throws ELException {
            return variables.get(name);
        }
    }
}
