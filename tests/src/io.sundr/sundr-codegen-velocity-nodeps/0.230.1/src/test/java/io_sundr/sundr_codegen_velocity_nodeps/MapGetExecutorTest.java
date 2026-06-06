/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.velocity.runtime.log.Log;
import io.sundr.deps.org.apache.velocity.runtime.log.NullLogChute;
import io.sundr.deps.org.apache.velocity.runtime.parser.node.MapGetExecutor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MapGetExecutorTest {
    @Test
    void discoversMapGetMethodAndReadsConfiguredProperty() throws Exception {
        MapGetExecutor executor = new MapGetExecutor(
                new Log(new NullLogChute()),
                LinkedHashMap.class,
                "language");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("language", "Java");
        values.put("tool", "Velocity");

        Object result = executor.execute(values);

        assertThat(executor.isAlive()).isTrue();
        assertThat(result).isEqualTo("Java");
    }
}
