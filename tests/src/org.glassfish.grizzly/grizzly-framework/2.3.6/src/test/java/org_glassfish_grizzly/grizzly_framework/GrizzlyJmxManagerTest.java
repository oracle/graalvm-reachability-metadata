/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.jmxbase.GrizzlyJmxManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GrizzlyJmxManagerTest {
    @Test
    void instanceLoadsDefaultJmxManagerWhenMonitoringImplementationIsAvailable() {
        GrizzlyJmxManager manager = GrizzlyJmxManager.instance();

        assertThat(manager).isNotNull();
        assertThat(manager.getClass().getName()).isEqualTo("org.glassfish.grizzly.monitoring.jmx.DefaultJmxManager");
    }
}
