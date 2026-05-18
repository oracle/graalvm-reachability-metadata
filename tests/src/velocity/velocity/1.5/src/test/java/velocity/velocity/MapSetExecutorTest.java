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
import org.apache.velocity.runtime.parser.node.MapSetExecutor;
import org.junit.jupiter.api.Test;

public class MapSetExecutorTest {
    @Test
    void constructorDiscoversMapPutMethodAndExecuteStoresValue() throws Exception {
        final Log log = new Log(new NullLogChute());
        final MapSetExecutor executor = new MapSetExecutor(log, HashMap.class, "answer");
        final Map<String, String> values = new HashMap<>();

        final Object previousValue = executor.execute(values, "forty-two");

        assertThat(executor.isAlive()).isTrue();
        assertThat(previousValue).isNull();
        assertThat(values).containsEntry("answer", "forty-two");
    }
}
