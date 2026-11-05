/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_observability_api;

import io.micrometer.common.KeyValue;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.observability.ModulithContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ModulithContextTest {

    @Test
    void describesModuleBeanInvocation() throws Throwable {
        ModulithContext context = ObservationFixtures.context(false);

        assertThat(context.getApplicationName()).isEqualTo("warehouse");
        assertThat(context.getModule().getIdentifier().toString()).isEqualTo("inventory");
        assertThat(context.getInvocation().proceed()).isEqualTo("reserved");
        assertThat(context.getContextualName()).isEqualTo("Inventory > InventoryService.reserve()");
        assertThat(context.getLowCardinalityValues()).containsExactlyInAnyOrder(
                KeyValue.of("module.identifier", "inventory"),
                KeyValue.of("module.name", "Inventory"),
                KeyValue.of("module.invocation-type", "bean"));
        assertThat(context.getHighCardinalityValues()).containsExactly(KeyValue.of("module.method", "reserve"));
    }

    @Test
    void distinguishesEventListenerInvocationsFromBeanCalls() {
        ModulithContext context = ObservationFixtures.context(true);

        assertThat(context.getLowCardinalityValues()).containsExactlyInAnyOrder(
                KeyValue.of("module.identifier", "inventory"),
                KeyValue.of("module.name", "Inventory"),
                KeyValue.of("module.invocation-type", "event-listener"));
        assertThat(context.getHighCardinalityValues()).containsExactly(KeyValue.of("module.method", "reserve"));
    }
}
