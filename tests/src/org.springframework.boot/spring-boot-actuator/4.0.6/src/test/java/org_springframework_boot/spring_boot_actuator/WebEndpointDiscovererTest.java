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
import org.springframework.boot.actuate.endpoint.InvocationContext;
import org.springframework.boot.actuate.endpoint.Operation;
import org.springframework.boot.actuate.endpoint.OperationType;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.EndpointDiscoverer;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.invoke.convert.ConversionServiceParameterValueMapper;
import org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.WebEndpointHttpMethod;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.WebOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpointDiscoverer;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class WebEndpointDiscovererTest {

    @Test
    void discoversAndInvokesAnApplicationInfoEndpoint() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(InfoEndpoint.class,
                    () -> new InfoEndpoint(List.of(builder -> builder.withDetail("service", "orders"))));
            context.refresh();
            EndpointDiscoverer<ExposableWebEndpoint, WebOperation> discoverer = new WebEndpointDiscoverer(context,
                    new ConversionServiceParameterValueMapper(),
                    new EndpointMediaTypes(List.of("application/json"), List.of("application/json")),
                    List.of(id -> "application/" + id), List.of(), List.of(), List.of(), List.of());

            Collection<ExposableWebEndpoint> endpoints = discoverer.getEndpoints();

            assertThat(endpoints).hasSize(1);
            ExposableWebEndpoint endpoint = endpoints.iterator().next();
            assertThat(endpoint.getEndpointId().toString()).isEqualTo("info");
            assertThat(endpoint.getDefaultAccess()).isEqualTo(Access.UNRESTRICTED);
            assertThat(endpoint.getRootPath()).isEqualTo("application/info");
            assertThat(endpoint.getOperations()).hasSize(1);
            WebOperation operation = endpoint.getOperations().iterator().next();
            assertThat(operation.getId()).isEqualTo("info");
            assertThat(operation.isBlocking()).isTrue();
            assertThat(operation.getType()).isEqualTo(OperationType.READ);
            assertThat(operation.getRequestPredicate().getHttpMethod()).isEqualTo(WebEndpointHttpMethod.GET);
            assertThat(operation.getRequestPredicate().getPath()).isEqualTo("application/info");
            assertThat(operation.getRequestPredicate().getProduces()).containsExactly("application/json");
            assertThat(operation.invoke(new InvocationContext(SecurityContext.NONE, Map.of())))
                    .isEqualTo(Map.of("service", "orders"));
            assertThat(new EndpointLinksResolver(endpoints).resolveLinks("https://example.test/manage/")
                    .get("info").getHref()).isEqualTo("https://example.test/manage/application/info");
            assertThat(discoverer.getEndpoints()).isSameAs(endpoints);
        }
    }

    @Test
    void webExtensionReplacesThePortableReadOperation() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(QueueEndpoint.class, QueueEndpoint::new);
            context.registerBean(QueueWebExtension.class, QueueWebExtension::new);
            context.refresh();
            WebEndpointDiscoverer discoverer = new WebEndpointDiscoverer(context,
                    new ConversionServiceParameterValueMapper(),
                    new EndpointMediaTypes(List.of("application/json"), List.of("application/json")),
                    List.of(), List.of(), List.of(), List.of(), List.of());

            Collection<ExposableWebEndpoint> endpoints = discoverer.getEndpoints();

            assertThat(endpoints).hasSize(1);
            ExposableWebEndpoint endpoint = endpoints.iterator().next();
            assertThat(endpoint.getEndpointId().toString()).isEqualTo("queue");
            assertThat(endpoint.getDefaultAccess()).isEqualTo(Access.READ_ONLY);
            assertThat(endpoint.getOperations()).hasSize(1);
            Operation operation = endpoint.getOperations().iterator().next();
            Object result = operation.invoke(new InvocationContext(SecurityContext.NONE, Map.of()));
            assertThat(result).isInstanceOf(WebEndpointResponse.class);
            WebEndpointResponse<?> response = (WebEndpointResponse<?>) result;
            assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
            assertThat(response.getBody()).isEqualTo(Map.of("pending", 7, "transport", "web"));
        }
    }

    @Endpoint(id = "queue", defaultAccess = Access.READ_ONLY)
    public static class QueueEndpoint {

        @ReadOperation
        public Map<String, Object> read() {
            return Map.of("pending", 7);
        }
    }

    @EndpointWebExtension(endpoint = QueueEndpoint.class)
    public static class QueueWebExtension {

        @ReadOperation
        public WebEndpointResponse<Map<String, Object>> read() {
            return new WebEndpointResponse<>(Map.of("pending", 7, "transport", "web"));
        }
    }
}
