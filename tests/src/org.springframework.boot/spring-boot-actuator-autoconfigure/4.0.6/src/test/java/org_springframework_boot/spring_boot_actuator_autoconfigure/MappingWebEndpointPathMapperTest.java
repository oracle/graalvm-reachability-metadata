/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator_autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.actuate.endpoint.web.PathMapper;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;

public class MappingWebEndpointPathMapperTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EndpointAutoConfiguration.class,
                    WebEndpointAutoConfiguration.class, InfoEndpointAutoConfiguration.class))
            .withUserConfiguration(FallbackPathConfiguration.class)
            .withPropertyValues("management.endpoints.web.exposure.include=info",
                    "management.endpoints.web.base-path=/manage");

    @Test
    void configuredPathTakesPrecedenceOverOtherMappers() {
        runner.withPropertyValues("management.endpoints.web.path-mapping.info=details")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(WebEndpointsSupplier.class).getEndpoints())
                            .singleElement().satisfies(endpoint -> assertThat(endpoint.getRootPath()).isEqualTo("details"));
                    assertThat(context.getBean(PathMappedEndpoints.class).getPath(EndpointId.of("info")))
                            .isEqualTo("/manage/details");
                });
    }

    @Test
    void unmappedEndpointUsesTheNextMapper() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PathMappedEndpoints.class).getPath(EndpointId.of("info")))
                    .isEqualTo("/manage/fallback-info");
        });
    }

    @Configuration(proxyBeanMethods = false)
    public static class FallbackPathConfiguration {

        @Bean
        @Order(0)
        public PathMapper fallbackPathMapper() {
            return endpointId -> "fallback-" + endpointId;
        }

    }

}
