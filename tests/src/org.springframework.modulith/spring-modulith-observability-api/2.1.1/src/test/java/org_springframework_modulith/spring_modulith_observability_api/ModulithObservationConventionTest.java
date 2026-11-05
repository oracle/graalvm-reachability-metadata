/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_observability_api;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.observability.ModulithContext;
import org.springframework.modulith.observability.ModulithObservationConvention;

import static org.assertj.core.api.Assertions.assertThat;

public class ModulithObservationConventionTest {

    @Test
    void suppliesNamesAndTagsForModuleContextsOnly() {
        ModulithObservationConvention convention = new ModulithObservationConvention() { };
        ModulithContext context = ObservationFixtures.context(false);
        context.addHighCardinalityKeyValue(KeyValue.of("reservation.id", "reservation-42"));

        assertThat(convention.supportsContext(context)).isTrue();
        assertThat(convention.supportsContext(new Observation.Context())).isFalse();
        assertThat(convention.getName()).isEqualTo("module.invocations");
        assertThat(convention.getContextualName(context)).isEqualTo("Inventory > InventoryService.reserve()");
        assertThat(convention.getLowCardinalityKeyValues(context)).isEqualTo(context.getLowCardinalityValues());
        assertThat(convention.getHighCardinalityKeyValues(context))
                .containsExactly(KeyValue.of("reservation.id", "reservation-42"));
    }

    @Test
    void recordsModuleInvocationsWithLowCardinalityMeterTags() throws Throwable {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        try {
            ObservationRegistry registry = ObservationRegistry.create();
            registry.observationConfig().observationHandler(new DefaultMeterObservationHandler(meters));
            ModulithContext context = ObservationFixtures.context(false);
            context.addHighCardinalityKeyValue(KeyValue.of("reservation.id", "reservation-42"));
            ModulithObservationConvention convention = new ModulithObservationConvention() { };
            Observation observation = Observation.createNotStarted(convention, () -> context, registry);

            Object result = observation.observeChecked(() -> context.getInvocation().proceed());

            assertThat(result).isEqualTo("reserved");
            assertThat(context.getName()).isEqualTo("module.invocations");
            assertThat(context.getContextualName()).isEqualTo("Inventory > InventoryService.reserve()");
            assertThat(context.getHighCardinalityKeyValue("reservation.id").getValue())
                    .isEqualTo("reservation-42");
            Timer timer = meters.get("module.invocations")
                    .tags("module.identifier", "inventory", "module.name", "Inventory",
                            "module.invocation-type", "bean", "error", "none").timer();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.getId().getTag("reservation.id")).isNull();
            assertThat(registry.getCurrentObservation()).isNull();
        } finally {
            meters.close();
        }
    }
}
