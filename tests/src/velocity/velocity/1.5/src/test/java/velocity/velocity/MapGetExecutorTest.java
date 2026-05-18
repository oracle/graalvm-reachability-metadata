/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.runtime.parser.node.MapGetExecutor;
import org.junit.jupiter.api.Test;

public class MapGetExecutorTest {
    @Test
    void constructorDiscoversMapGetMethodAndExecuteReturnsStoredValue() throws Exception {
        final Log log = new Log(new NullLogChute());
        final MapGetExecutor executor = new MapGetExecutor(log, HashMap.class, "answer");
        final Map<String, String> values = new HashMap<>();
        values.put("answer", "forty-two");

        final Object result = executor.execute(values);

        assertThat(executor.isAlive()).isTrue();
        assertThat(result).isEqualTo("forty-two");
    }
}
