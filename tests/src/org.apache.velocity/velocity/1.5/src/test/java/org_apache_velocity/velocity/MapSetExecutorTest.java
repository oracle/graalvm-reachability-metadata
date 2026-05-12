/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.runtime.parser.node.MapSetExecutor;
import org.junit.jupiter.api.Test;

public class MapSetExecutorTest {
    @Test
    void discoversMapPutMethodAndWritesConfiguredProperty() throws IllegalAccessException, InvocationTargetException {
        MapSetExecutor executor = new MapSetExecutor(new Log(new NullLogSystem()), HashMap.class, "name");
        Map<String, Object> values = new HashMap<>();

        Object previous = executor.execute(values, "Ada");

        assertThat(executor.isAlive()).isTrue();
        assertThat(previous).isNull();
        assertThat(values).containsEntry("name", "Ada");
    }
}
