/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.logging.annotations.Feature;
import oracle.jdbc.logging.runtime.TraceControllerImpl;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@Order(0)
public class TraceControllerImplTest {
    @Test
    void readsPackagedTracingOptionsAndSuspendsWithoutLosingThem() {
        String property = "java.util.logging.config.file";
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "oracle-tracing.properties");
            TraceControllerImpl controller = new TraceControllerImpl();
            Feature[] enabled = controller.getEnabledFeatures();
            try {
                assertThat(controller.isTracing(Feature.CONNECT)).isTrue();
                assertThat(controller.isTracing(Feature.SQL_EXECUTION)).isFalse();
                controller.suspend();
                try {
                    assertThat(controller.isTracing(Feature.CONNECT)).isFalse();
                    assertThat(controller.getEnabledFeatures()).isEmpty();
                } finally {
                    controller.resume();
                }
                assertThat(controller.isTracing(Feature.CONNECT)).isTrue();
                controller.trace(true, Feature.SQL_EXECUTION);
                assertThat(controller.isTracing(Feature.SQL_EXECUTION)).isTrue();
            } finally {
                controller.trace(false, Feature.ALL);
                controller.trace(true, enabled);
            }
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }
}
