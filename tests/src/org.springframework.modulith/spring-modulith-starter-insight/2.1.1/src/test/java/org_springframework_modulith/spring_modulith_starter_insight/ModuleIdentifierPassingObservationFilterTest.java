/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_starter_insight;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.observability.ModulithObservations.LowKeys;
import org.springframework.modulith.observability.support.ModuleIdentifierPassingObservationFilter;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleIdentifierPassingObservationFilterTest {

    @Test
    void propagatesParentModuleIdentifierToChildMetrics() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        try {
            ObservationRegistry registry = observationRegistry(meters);
            Observation parent = Observation.start("orders.entry", registry)
                    .lowCardinalityKeyValue(LowKeys.MODULE_IDENTIFIER.withValue("orders"));
            try (Observation.Scope scope = parent.openScope()) {
                Observation child = Observation.start("orders.database", registry);
                child.stop();

                assertThat(child.getContext().getLowCardinalityKeyValue("module.identifier").getValue())
                        .isEqualTo("orders");
                assertThat(meters.get("orders.database").tag("module.identifier", "orders").timer().count())
                        .isEqualTo(1);
            } finally {
                parent.stop();
            }
        } finally {
            meters.close();
        }
    }

    @Test
    void preservesChildModuleIdentifierAcrossModuleBoundaries() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        try {
            ObservationRegistry registry = observationRegistry(meters);
            Observation parent = Observation.start("orders.entry", registry)
                    .lowCardinalityKeyValue(LowKeys.MODULE_IDENTIFIER.withValue("orders"));
            try (Observation.Scope scope = parent.openScope()) {
                Observation child = Observation.start("inventory.entry", registry)
                        .lowCardinalityKeyValue(LowKeys.MODULE_IDENTIFIER.withValue("inventory"));
                child.stop();

                assertThat(child.getContext().getLowCardinalityKeyValue("module.identifier").getValue())
                        .isEqualTo("inventory");
                assertThat(meters.get("inventory.entry").tag("module.identifier", "inventory").timer().count())
                        .isEqualTo(1);
                assertThat(meters.find("inventory.entry").tag("module.identifier", "orders").timer()).isNull();
            } finally {
                parent.stop();
            }
        } finally {
            meters.close();
        }
    }

    @Test
    void leavesRootObservationsWithoutAModuleUntagged() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        try {
            ObservationRegistry registry = observationRegistry(meters);
            Observation observation = Observation.start("http.request", registry);
            observation.stop();

            assertThat(observation.getContext().getLowCardinalityKeyValue("module.identifier")).isNull();
            assertThat(meters.get("http.request").timer().getId().getTag("module.identifier")).isNull();
            assertThat(meters.get("http.request").timer().count()).isEqualTo(1);
        } finally {
            meters.close();
        }
    }

    @Test
    void leavesChildrenOfUntaggedParentsWithoutAModule() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        try {
            ObservationRegistry registry = observationRegistry(meters);
            Observation parent = Observation.start("http.request", registry);
            try (Observation.Scope scope = parent.openScope()) {
                Observation child = Observation.start("http.decode", registry);
                child.stop();

                assertThat(child.getContext().getLowCardinalityKeyValue("module.identifier")).isNull();
                assertThat(meters.get("http.decode").timer().getId().getTag("module.identifier")).isNull();
                assertThat(meters.get("http.decode").timer().count()).isEqualTo(1);
            } finally {
                parent.stop();
            }
        } finally {
            meters.close();
        }
    }

    private static ObservationRegistry observationRegistry(MeterRegistry meters) {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new DefaultMeterObservationHandler(meters))
                .observationFilter(new ModuleIdentifierPassingObservationFilter());
        return registry;
    }
}
