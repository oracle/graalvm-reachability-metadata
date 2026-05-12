/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.runtime.parser.node.MapGetExecutor;
import org.junit.jupiter.api.Test;

public class MapGetExecutorTest {
    @Test
    void discoversMapGetMethodAndReadsConfiguredProperty() {
        MapGetExecutor executor = new MapGetExecutor(new Log(new NullLogSystem()), HashMap.class, "name");

        Map<String, Object> values = new HashMap<>();
        values.put("name", "Ada");
        values.put("other", "ignored");

        assertThat(executor.isAlive()).isTrue();
        assertThat(executor.execute(values)).isEqualTo("Ada");
    }
}
