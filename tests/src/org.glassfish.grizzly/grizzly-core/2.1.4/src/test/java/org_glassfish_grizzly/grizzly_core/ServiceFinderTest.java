/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_core;

import org.glassfish.grizzly.utils.ServiceFinder;
import org.junit.jupiter.api.Test;
import org_glassfish_grizzly.grizzly_core.ServiceFinderInnerLazyIteratorTest.GreetingService;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceFinderTest {
    @Test
    void materializesDiscoveredServicesIntoATypedArray() {
        GreetingService[] services = ServiceFinder.find(GreetingService.class).toArray();

        assertThat(services).hasSize(1);
        assertThat(services[0].greet("application")).isEqualTo("Hello, application!");
    }
}
