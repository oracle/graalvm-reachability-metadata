/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_events_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.jmolecules.event.annotation.Externalized;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

public class AnnotationTargetLookupTest {

    @Test
    void recognizesJMoleculesExternalizedEventsAndUsesTheirRoutingTarget() {
        JMoleculesOrderPlaced event = new JMoleculesOrderPlaced("order-73");

        EventExternalizationConfiguration configuration = EventExternalizationConfiguration.externalizing()
                .select(EventExternalizationConfiguration.annotatedAsExternalized())
                .build();

        assertThat(configuration.supports(event)).isTrue();
        assertThat(configuration.determineTarget(event))
                .isEqualTo(RoutingTarget.forTarget("jmolecules.orders").andKey("#{#this.orderId}"));
    }

    @Externalized(target = "jmolecules.orders::#{#this.orderId}")
    public record JMoleculesOrderPlaced(String orderId) {}
}
