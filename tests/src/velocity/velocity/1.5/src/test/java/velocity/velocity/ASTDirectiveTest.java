/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.Arrays;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.junit.jupiter.api.Test;

public class ASTDirectiveTest {
    @Test
    public void instantiatesRegisteredDirectiveWhenInitializingParsedTemplate() throws Exception {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        velocityEngine.init();

        VelocityContext context = new VelocityContext();
        context.put("items", Arrays.asList("alpha", "beta", "gamma"));

        StringWriter writer = new StringWriter();
        boolean evaluated = velocityEngine.evaluate(
                context,
                writer,
                "ASTDirectiveTest",
                "#foreach($item in $items)$item;#end");

        assertThat(evaluated).isTrue();
        assertThat(writer).hasToString("alpha;beta;gamma;");
    }
}
