/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.endpoint.Access;
import org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver;
import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpointDiscoverer;
import org.springframework.boot.actuate.endpoint.web.annotation.ExposableControllerEndpoint;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("removal")
public class ControllerEndpointDiscovererTest {

    @Test
    void exposesTheControllerWithItsConfiguredAccessAndMappedPath() {
        StatisticsController controller = new StatisticsController();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(StatisticsController.class, () -> controller);
            context.refresh();
            ControllerEndpointDiscoverer discoverer = new ControllerEndpointDiscoverer(context,
                    List.of(id -> "legacy/" + id), List.of());

            Collection<ExposableControllerEndpoint> endpoints = discoverer.getEndpoints();

            assertThat(endpoints).hasSize(1);
            ExposableControllerEndpoint endpoint = endpoints.iterator().next();
            assertThat(endpoint.getEndpointId().toString()).isEqualTo("statistics");
            assertThat(endpoint.getDefaultAccess()).isEqualTo(Access.READ_ONLY);
            assertThat(endpoint.getRootPath()).isEqualTo("legacy/statistics");
            assertThat(endpoint.getOperations()).isEmpty();
            assertThat(endpoint.getController()).isSameAs(controller);
            assertThat(((StatisticsController) endpoint.getController()).statistics())
                    .containsEntry("processed", 23);
            assertThat(new EndpointLinksResolver(endpoints).resolveLinks("https://example.test/manage")
                    .get("statistics").getHref()).isEqualTo("https://example.test/manage/legacy/statistics");
        }
    }

    @RestControllerEndpoint(id = "statistics", defaultAccess = Access.READ_ONLY)
    public static class StatisticsController {

        @GetMapping
        public Map<String, Integer> statistics() {
            return Map.of("processed", 23);
        }
    }
}
