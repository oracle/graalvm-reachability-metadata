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
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.VariableResolver;
import org.apache.commons.el.BinaryOperatorExpression;
import org.apache.commons.el.ExpressionEvaluatorImpl;
import org.apache.commons.el.FloatingPointLiteral;
import org.apache.commons.el.IntegerLiteral;
import org.apache.commons.el.Logger;
import org.apache.commons.el.ModulusOperator;
import org.junit.jupiter.api.Test;

public class ModulusOperatorTest {
    @Test
    public void evaluatesFloatingPointRemainderThroughExpressionEvaluator() throws Exception {
        ExpressionEvaluatorImpl evaluator = new ExpressionEvaluatorImpl(true);
        VariableResolver resolver = new MapBackedVariableResolver(Map.of(
                "left", "9.5",
                "right", Integer.valueOf(4)));

        Object result = evaluator.evaluate("${left % right}", Object.class, resolver, null);

        assertThat(result).isInstanceOf(Double.class);
        assertThat((Double) result).isEqualTo(1.5D);
    }

    @Test
    public void evaluatesFloatingPointRemainderThroughExpressionTree() throws Exception {
        Logger logger = newDiscardingLogger();
        BinaryOperatorExpression expression = new BinaryOperatorExpression(
                new FloatingPointLiteral("9.5"),
                List.of(ModulusOperator.SINGLETON),
                List.of(new IntegerLiteral("4")));

        Object result = expression.evaluate(null, null, logger);

        assertThat(expression.getExpressionString()).isEqualTo("(9.5 % 4)");
        assertThat(result).isInstanceOf(Double.class);
        assertThat((Double) result).isEqualTo(1.5D);
    }

    @Test
    public void evaluatesBigIntegerRemainderThroughExpressionEvaluator() throws Exception {
        ExpressionEvaluatorImpl evaluator = new ExpressionEvaluatorImpl(true);
        VariableResolver resolver = new MapBackedVariableResolver(Map.of(
                "left", new BigInteger("17"),
                "right", new BigInteger("5")));

        Object result = evaluator.evaluate("${left mod right}", Object.class, resolver, null);

        assertThat(result).isInstanceOf(BigInteger.class);
        assertThat(result).isEqualTo(new BigInteger("2"));
    }

    @Test
    public void evaluatesIntegerRemainderThroughExpressionEvaluator() throws Exception {
        ExpressionEvaluatorImpl evaluator = new ExpressionEvaluatorImpl(true);
        VariableResolver resolver = new MapBackedVariableResolver(Map.of(
                "left", Long.valueOf(9L),
                "right", Integer.valueOf(4)));

        Object result = evaluator.evaluate("${left % right}", Object.class, resolver, null);

        assertThat(result).isInstanceOf(Long.class);
        assertThat(result).isEqualTo(Long.valueOf(1L));
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
