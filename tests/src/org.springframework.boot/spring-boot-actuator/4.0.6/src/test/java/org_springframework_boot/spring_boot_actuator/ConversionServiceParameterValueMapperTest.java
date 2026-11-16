/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventsEndpoint;
import org.springframework.boot.actuate.audit.AuditEventsEndpoint.AuditEventsDescriptor;
import org.springframework.boot.actuate.audit.InMemoryAuditEventRepository;
import org.springframework.boot.actuate.endpoint.InvocationContext;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.annotation.EndpointConverter;
import org.springframework.boot.actuate.endpoint.invoke.convert.ConversionServiceParameterValueMapper;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.WebOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpointDiscoverer;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.convert.converter.Converter;

import static org.assertj.core.api.Assertions.assertThat;

public class ConversionServiceParameterValueMapperTest {

    @Test
    void qualifiedConverterMapsAnAuditQueryParameterBeforeInvocation() {
        Instant boundary = Instant.parse("2025-01-01T12:00:00Z");
        AuditEvent oldEvent = new AuditEvent(boundary.minusSeconds(60), "alice", "LOGIN", Map.of());
        AuditEvent matchingEvent = new AuditEvent(boundary.plusSeconds(60), "alice", "LOGIN", Map.of("ip", "192.0.2.1"));
        InMemoryAuditEventRepository repository = new InMemoryAuditEventRepository();
        repository.add(oldEvent);
        repository.add(matchingEvent);
        repository.add(new AuditEvent(boundary.plusSeconds(120), "bob", "LOGIN", Map.of()));
        repository.add(new AuditEvent(boundary.plusSeconds(180), "alice", "LOGOUT", Map.of()));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AuditEventsEndpoint.class, () -> new AuditEventsEndpoint(repository));
            context.registerBean("endpointConverter", AuditWindowConverter.class, AuditWindowConverter::new);
            context.registerBean("unqualifiedConverter", OtherWindowConverter.class, OtherWindowConverter::new);
            context.registerBean(EndpointConversion.class);
            context.refresh();
            WebEndpointDiscoverer discoverer = new WebEndpointDiscoverer(context,
                    new ConversionServiceParameterValueMapper(context.getBean(EndpointConversion.class).service),
                    new EndpointMediaTypes(List.of("application/json"), List.of("application/json")),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            assertThat(discoverer.getEndpoints()).hasSize(1);
            ExposableWebEndpoint endpoint = discoverer.getEndpoints().iterator().next();
            assertThat(endpoint.getOperations()).hasSize(1);
            WebOperation operation = endpoint.getOperations().iterator().next();

            Object result = operation.invoke(new InvocationContext(SecurityContext.NONE,
                    Map.of("principal", "alice", "after", "current-window", "type", "LOGIN")));

            assertThat(result).isInstanceOf(AuditEventsDescriptor.class);
            assertThat(((AuditEventsDescriptor) result).getEvents()).containsExactly(matchingEvent);
        }
    }

    public static class EndpointConversion {

        private final ApplicationConversionService service = new ApplicationConversionService();

        public EndpointConversion(@EndpointConverter List<Converter<?, ?>> converters) {
            converters.forEach(this.service::addConverter);
        }
    }

    @EndpointConverter
    public static class AuditWindowConverter implements Converter<String, OffsetDateTime> {

        @Override
        public OffsetDateTime convert(String source) {
            return "current-window".equals(source) ? OffsetDateTime.parse("2025-01-01T12:00:00Z")
                    : OffsetDateTime.parse(source);
        }
    }

    public static class OtherWindowConverter implements Converter<String, OffsetDateTime> {

        @Override
        public OffsetDateTime convert(String source) {
            return OffsetDateTime.parse("2025-01-02T12:00:00Z");
        }
    }
}
