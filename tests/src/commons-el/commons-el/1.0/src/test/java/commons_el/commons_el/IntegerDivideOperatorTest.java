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
import java.util.List;
import org.apache.commons.el.BinaryOperatorExpression;
import org.apache.commons.el.IntegerDivideOperator;
import org.apache.commons.el.IntegerLiteral;
import org.apache.commons.el.Logger;
import org.junit.jupiter.api.Test;

public class IntegerDivideOperatorTest {
    @Test
    public void appliesIntegerDivisionToPublicOperatorOperands() throws Exception {
        Logger logger = newDiscardingLogger();

        Object result = IntegerDivideOperator.SINGLETON.apply(
                Long.valueOf(7L),
                Integer.valueOf(2),
                logger);

        assertThat(result).isInstanceOf(Long.class);
        assertThat(result).isEqualTo(Long.valueOf(3L));
    }

    @Test
    public void coercesStringOperandsBeforeIntegerDivision() throws Exception {
        Logger logger = newDiscardingLogger();

        Object result = IntegerDivideOperator.SINGLETON.apply("9", "4", logger);

        assertThat(result).isInstanceOf(Long.class);
        assertThat(result).isEqualTo(Long.valueOf(2L));
    }

    @Test
    public void evaluatesIntegerDivisionThroughExpressionTree() throws Exception {
        Logger logger = newDiscardingLogger();
        BinaryOperatorExpression expression = new BinaryOperatorExpression(
                new IntegerLiteral("9"),
                List.of(IntegerDivideOperator.SINGLETON),
                List.of(new IntegerLiteral("2")));

        Object result = expression.evaluate(null, null, logger);

        assertThat(expression.getExpressionString()).isEqualTo("(9 idiv 2)");
        assertThat(result).isEqualTo(Long.valueOf(4L));
    }

    private static Logger newDiscardingLogger() {
        return new Logger(new PrintStream(OutputStream.nullOutputStream()));
    }
}
