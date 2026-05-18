/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.monitoring.MonitoringUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MonitoringUtilsTest {
    @Test
    void loadJmxObjectCreatesObjectWithExplicitConstructorType() {
        String message = "grizzly-monitoring";

        Object jmxObject = MonitoringUtils.loadJmxObject(TransformationException.class.getName(), message, String.class);

        assertThat(jmxObject).isInstanceOf(TransformationException.class);
        assertThat(((TransformationException) jmxObject).getMessage()).isEqualTo(message);
    }
}
