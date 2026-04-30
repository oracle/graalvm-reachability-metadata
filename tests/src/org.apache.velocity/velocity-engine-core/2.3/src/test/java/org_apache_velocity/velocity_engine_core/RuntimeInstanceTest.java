/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity_engine_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.runtime.RuntimeInstance;
import org.junit.jupiter.api.Test;

public class RuntimeInstanceTest {
    @Test
    void initializesDefaultRuntimeAndCreatesParser() {
        RuntimeInstance runtimeInstance = new RuntimeInstance();

        runtimeInstance.init();

        assertThat(runtimeInstance.isInitialized()).isTrue();
        assertThat(runtimeInstance.getDirective("foreach")).isNotNull();
        assertThat(runtimeInstance.createNewParser()).isNotNull();
    }
}
