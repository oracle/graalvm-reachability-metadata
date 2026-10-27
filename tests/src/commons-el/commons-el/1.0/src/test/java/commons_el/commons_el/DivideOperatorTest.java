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
import java.util.Map;
import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.VariableResolver;
import org.apache.commons.el.DivideOperator;
import org.apache.commons.el.ExpressionEvaluatorImpl;
import org.apache.commons.el.Logger;
import org.junit.jupiter.api.Test;

public class DivideOperatorTest {
    @Test
    public void appliesBigDecimalDivisionToPublicOperatorOperands() throws Exception {
        Logger logger = newDiscardingLogger();

        Object result = DivideOperator.SINGLETON.apply(
                new BigDecimal("9.0"),
                new BigDecimal("3"),
                logger);

        assertThat(result).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) result).isEqualByComparingTo("3.0");
    }

    @Test
    public void appliesDoubleDivisionToPublicOperatorOperands() throws Exception {
        Logger logger = newDiscardingLogger();

        Object result = DivideOperator.SINGLETON.apply(
                Integer.valueOf(9),
                Integer.valueOf(3),
                logger);

        assertThat(result).isInstanceOf(Double.class);
        assertThat((Double) result).isEqualTo(3.0D);
    }

    @Test
    public void evaluatesBigDecimalDivisionWithResolvedVariables() throws Exception {
        ExpressionEvaluatorImpl evaluator = new ExpressionEvaluatorImpl(true);
        VariableResolver resolver = new MapBackedVariableResolver(Map.of(
                "left", new BigDecimal("9.0"),
                "right", new BigDecimal("3")));

        Object result = evaluator.evaluate("${left / right}", Object.class, resolver, null);

        assertThat(result).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) result).isEqualByComparingTo("3.0");
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
