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
import io.sundr.deps.org.apache.velocity.runtime.parser.node.MapSetExecutor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MapSetExecutorTest {
    @Test
    void discoversMapPutMethodAndWritesConfiguredProperty() throws Exception {
        MapSetExecutor executor = new MapSetExecutor(
                new Log(new NullLogChute()),
                LinkedHashMap.class,
                "language");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("language", "Java");

        Object previousValue = executor.execute(values, "Velocity");

        assertThat(executor.isAlive()).isTrue();
        assertThat(previousValue).isEqualTo("Java");
        assertThat(values).containsEntry("language", "Velocity");
    }
}
