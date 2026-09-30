/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_tf;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.pfl.tf.spi.MethodMonitorRegistry;
import org.junit.jupiter.api.Test;

public class MethodMonitorRegistryTest {
    @Test
    void loadsMonitorAnnotationsFromAResourceBundle() {
        MethodMonitorRegistry.registerAnnotationFile("org_glassfish_pfl_pfl_tf.monitors");

        assertThat(MethodMonitorRegistry.getMMAnnotations())
                .contains("org_glassfish_pfl.pfl_tf.RegistryMonitor");
    }
}
