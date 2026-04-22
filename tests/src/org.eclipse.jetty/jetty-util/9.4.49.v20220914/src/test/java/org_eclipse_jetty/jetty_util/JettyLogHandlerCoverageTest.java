/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.log.JettyLogHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JettyLogHandlerCoverageTest {
    @Test
    void jettyLogHandlerLoadsLoggingConfigurationFromResources() {
        JettyLogHandler.config();

        assertThat(System.getProperty("org.apache.commons.logging.Log")).isEqualTo("org.apache.commons.logging.impl.Jdk14Logger");
    }
}
