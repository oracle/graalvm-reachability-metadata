/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.runtime.parser.node.GetExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

public class GetExecutorTest {
    @Test
    void invokesDiscoveredGetMethodWithConfiguredPropertyName() throws Exception {
        Log log = new Log(new NullLogSystem());
        GetExecutor executor = new GetExecutor(log, new Introspector(log), PropertyLookup.class, "title");
        PropertyLookup lookup = new PropertyLookup();

        assertThat(executor.isAlive()).isTrue();
        assertThat(executor.execute(lookup)).isEqualTo("value for title");
        assertThat(lookup.requestedProperty).isEqualTo("title");
    }

    public static final class PropertyLookup {
        private String requestedProperty;

        public String get(String propertyName) {
            requestedProperty = propertyName;
            return "value for " + propertyName;
        }
    }
}
