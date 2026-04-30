/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity_engine_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.runtime.parser.node.PropertyExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyExecutorTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyExecutorTest.class);

    @Test
    void invokesDiscoveredGetterMethod() throws Exception {
        VelocityContext context = new VelocityContext();
        context.put("first", "value");
        Introspector introspector = new Introspector(LOGGER);
        PropertyExecutor executor = new PropertyExecutor(LOGGER, introspector, VelocityContext.class, "keys");

        Object keys = executor.execute(context);

        assertThat(keys).isInstanceOf(String[].class);
        assertThat((String[]) keys).containsExactly("first");
    }
}
