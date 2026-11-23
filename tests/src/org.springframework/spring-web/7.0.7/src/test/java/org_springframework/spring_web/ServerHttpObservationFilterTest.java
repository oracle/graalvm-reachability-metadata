/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ServerHttpObservationFilter;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerHttpObservationFilterTest {

    @Test
    void recordsRequestObservationAndResponseStatus() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        List<Observation.Context> completed = new ArrayList<>();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(Observation.Context context) {
                completed.add(context);
            }
        });
        ServerHttpObservationFilter filter = new ServerHttpObservationFilter(registry);
        filter.init(new MockFilterConfig("observations"));
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/items");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (incoming, outgoing) -> {
                assertThat(registry.getCurrentObservation()).isNotNull();
                assertThat(ServerHttpObservationFilter.findObservationContext(request)).isPresent();
                ((MockHttpServletResponse) outgoing).setStatus(202);
            });
            assertThat(completed).hasSize(1);
            assertThat(completed.get(0).getName()).isEqualTo("http.server.requests");
            assertThat(ServerHttpObservationFilter.findObservationContext(request).orElseThrow()
                    .getResponse().getStatus()).isEqualTo(202);
            assertThat(registry.getCurrentObservation()).isNull();
        } finally {
            filter.destroy();
        }
    }
}
