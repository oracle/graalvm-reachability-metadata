/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.junit.jupiter.api.Test;

public class MathUtilsTest {
    @Test
    public void evaluatesArithmeticExpressionsInTemplates() throws Exception {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        velocityEngine.init();

        VelocityContext context = new VelocityContext();
        context.put("left", Integer.valueOf(7));
        context.put("right", Short.valueOf((short) 5));

        StringWriter writer = new StringWriter();
        boolean evaluated = velocityEngine.evaluate(
                context,
                writer,
                "MathUtilsTest",
                "#set($sum = $left + $right)"
                        + "#set($product = $sum * 2)"
                        + "#set($quotient = $product / 3)"
                        + "#set($remainder = $product % 5)"
                        + "$sum,$product,$quotient,$remainder,"
                        + "#if($sum > $right)greater#end");

        assertThat(evaluated).isTrue();
        assertThat(writer).hasToString("12,24,8,4,greater");
    }
}
