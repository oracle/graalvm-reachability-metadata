/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.endpoint.jmx.JacksonJmxOperationResponseMapper;
import org.springframework.boot.actuate.info.InfoEndpoint;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonJmxOperationResponseMapperTest {

    @Test
    void mapsAnInfoOperationResponseToJmxCompatibleValues() {
        InfoEndpoint endpoint = new InfoEndpoint(List.of(
                builder -> builder.withDetail("service", "orders"),
                builder -> builder.withDetail("queue", Map.of("pending", 7, "available", true))));
        JacksonJmxOperationResponseMapper mapper = new JacksonJmxOperationResponseMapper(null);

        Object response = mapper.mapResponse(endpoint.info());

        assertThat(response).isEqualTo(Map.of("service", "orders", "queue", Map.of("pending", 7, "available", true)));
        assertThat(mapper.mapResponseType(endpoint.info().getClass())).isEqualTo(Map.class);
        assertThat(mapper.mapResponse(List.of(endpoint.info())))
                .isEqualTo(List.of(Map.of("service", "orders", "queue", Map.of("pending", 7, "available", true))));
    }
}
