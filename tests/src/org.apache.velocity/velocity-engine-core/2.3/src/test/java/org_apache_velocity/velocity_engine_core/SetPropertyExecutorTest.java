/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity_engine_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.apache.velocity.runtime.parser.node.SetPropertyExecutor;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetPropertyExecutorTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetPropertyExecutorTest.class);

    @Test
    void invokesDiscoveredSetterMethod() throws Exception {
        Date date = new Date(0L);
        Long expectedTime = 1234L;
        Introspector introspector = new Introspector(LOGGER);
        SetPropertyExecutor executor = new SetPropertyExecutor(LOGGER, introspector, Date.class, "time", expectedTime);

        Object result = executor.execute(date, expectedTime);

        assertThat(result).isNull();
        assertThat(date.getTime()).isEqualTo(expectedTime);
    }
}
