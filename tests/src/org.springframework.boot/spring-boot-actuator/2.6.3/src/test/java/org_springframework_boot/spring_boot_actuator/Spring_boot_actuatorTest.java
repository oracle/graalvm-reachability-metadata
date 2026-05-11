/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator;

import java.io.File;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventsEndpoint;
import org.springframework.boot.actuate.audit.InMemoryAuditEventRepository;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.ExposableEndpoint;
import org.springframework.boot.actuate.endpoint.Operation;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.Sanitizer;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver;
import org.springframework.boot.actuate.endpoint.web.EndpointMapping;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.Link;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoint;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.PingHealthIndicator;
import org.springframework.boot.actuate.health.SimpleHttpCodeStatusMapper;
import org.springframework.boot.actuate.health.SimpleStatusAggregator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.info.JavaInfoContributor;
import org.springframework.boot.actuate.info.MapInfoContributor;
import org.springframework.boot.actuate.system.DiskSpaceHealthIndicator;
import org.springframework.util.MimeType;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Spring_boot_actuatorTest {

    @Test
    void healthBuildersIndicatorsAndStatusStrategiesExposeExpectedState() {
        Health health = Health.down(new IllegalStateException("database offline"))
                .withDetail("component", "database")
                .build();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("component", "database");
        assertThat((String) health.getDetails().get("error"))
                .contains(IllegalStateException.class.getName(), "database offline");
        assertThatThrownBy(() -> health.getDetails().put("another", "value"))
                .isInstanceOf(UnsupportedOperationException.class);

        Status fatal = new Status("FATAL_ERROR");
        SimpleStatusAggregator defaultAggregator = new SimpleStatusAggregator();
        SimpleStatusAggregator customAggregator = new SimpleStatusAggregator("fatal-error", "down", "up");
        assertThat(defaultAggregator.getAggregateStatus(Set.of(Status.UP, Status.OUT_OF_SERVICE)))
                .isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(customAggregator.getAggregateStatus(Set.of(Status.UP, fatal))).isEqualTo(fatal);

        SimpleHttpCodeStatusMapper defaultStatusMapper = new SimpleHttpCodeStatusMapper();
        SimpleHttpCodeStatusMapper customStatusMapper = new SimpleHttpCodeStatusMapper(Map.of("fatal-error", 599));
        assertThat(defaultStatusMapper.getStatusCode(Status.DOWN))
                .isEqualTo(WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE);
        assertThat(defaultStatusMapper.getStatusCode(Status.UP)).isEqualTo(WebEndpointResponse.STATUS_OK);
        assertThat(customStatusMapper.getStatusCode(fatal)).isEqualTo(599);

        assertThat(new PingHealthIndicator().health().getStatus()).isEqualTo(Status.UP);

        Health diskHealth = new DiskSpaceHealthIndicator(new File("."), DataSize.ofBytes(0)).health();
        assertThat(diskHealth.getStatus()).isEqualTo(Status.UP);
        assertThat(diskHealth.getDetails())
                .containsEntry("exists", true)
                .containsEntry("threshold", 0L)
                .containsKeys("total", "free");
    }

    @Test
    void auditRepositoryAndEndpointFilterEventsByPrincipalTimeAndType() {
        Instant firstTimestamp = Instant.parse("2022-01-01T00:00:00Z");
        Instant secondTimestamp = Instant.parse("2022-01-01T00:01:00Z");
        Instant thirdTimestamp = Instant.parse("2022-01-01T00:02:00Z");
        InMemoryAuditEventRepository repository = new InMemoryAuditEventRepository(2);

        repository.add(new AuditEvent(firstTimestamp, "alice", "LOGIN", Map.of("ip", "10.0.0.1")));
        repository.add(new AuditEvent(secondTimestamp, "bob", "LOGIN", Map.of("ip", "10.0.0.2")));
        repository.add(new AuditEvent(thirdTimestamp, "bob", "LOGOUT", Map.of("reason", "user")));

        assertThat(repository.find(null, null, null))
                .extracting(AuditEvent::getType)
                .containsExactly("LOGIN", "LOGOUT");
        assertThat(repository.find("alice", null, null)).isEmpty();
        assertThat(repository.find("bob", secondTimestamp, null))
                .singleElement()
                .satisfies((event) -> {
                    assertThat(event.getType()).isEqualTo("LOGOUT");
                    assertThat(event.getData()).containsEntry("reason", "user");
                });

        AuditEventsEndpoint endpoint = new AuditEventsEndpoint(repository);
        OffsetDateTime afterFirstEvent = OffsetDateTime.ofInstant(firstTimestamp, ZoneOffset.UTC);
        AuditEventsEndpoint.AuditEventsDescriptor descriptor = endpoint.events("bob", afterFirstEvent, "LOGIN");
        assertThat(descriptor.getEvents())
                .singleElement()
                .satisfies((event) -> {
                    assertThat(event.getPrincipal()).isEqualTo("bob");
                    assertThat(event.getTimestamp()).isEqualTo(secondTimestamp);
                });
    }

    @Test
    void infoBuilderContributorsAndEndpointMergeTypedDetails() {
        Info info = new Info.Builder()
                .withDetail("name", "demo")
                .withDetails(Map.of("nested", Map.of("feature", "actuator"), "count", 2))
                .build();

        assertThat(info.get("name", String.class)).isEqualTo("demo");
        assertThat(info.get("nested", Map.class)).containsEntry("feature", "actuator");
        assertThat(info.getDetails()).containsEntry("count", 2);
        assertThatThrownBy(() -> info.get("count", String.class)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> info.getDetails().put("mutated", true))
                .isInstanceOf(UnsupportedOperationException.class);

        InfoEndpoint endpoint = new InfoEndpoint(List.of(
                new MapInfoContributor(Map.of("app", Map.of("name", "actuator-tests"))),
                (builder) -> builder.withDetail("build", Map.of("number", "42")),
                new JavaInfoContributor()));

        assertThat(endpoint.info())
                .containsKeys("app", "build", "java");
        assertThat(endpoint.info().get("build")).isEqualTo(Map.of("number", "42"));
    }

    @Test
    void endpointWebSupportNormalizesIdsPathsLinksMediaTypesAndResponses() {
        EndpointId dotted = EndpointId.of("health-check");
        EndpointId compact = EndpointId.of("healthcheck");
        assertThat(dotted).isEqualTo(compact);
        assertThat(dotted.toLowerCaseString()).isEqualTo("health-check");
        assertThat(EndpointId.fromPropertyValue("custom-endpoint").toString()).isEqualTo("customendpoint");
        assertThatThrownBy(() -> EndpointId.of("1invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not start with a number");

        Sanitizer sanitizer = new Sanitizer(List.of((SanitizingFunction) (data) -> {
            if ("visible".equals(data.getKey())) {
                return data.withValue("changed");
            }
            return data;
        }));
        assertThat(sanitizer.sanitize("spring.datasource.password", "secret"))
                .isEqualTo(SanitizableData.SANITIZED_VALUE);
        assertThat(sanitizer.sanitize("service.url", "https://user:secret@example.test/resource"))
                .isEqualTo("https://user:******@example.test/resource");
        assertThat(sanitizer.sanitize(new SanitizableData(null, "visible", "original"))).isEqualTo("changed");

        EndpointMapping mapping = new EndpointMapping("/actuator/");
        assertThat(mapping.getPath()).isEqualTo("/actuator");
        assertThat(mapping.createSubPath("health")).isEqualTo("/actuator/health");

        EndpointMediaTypes mediaTypes = new EndpointMediaTypes(List.of("application/test+json"), List.of("text/plain"));
        assertThat(mediaTypes.getProduced()).containsExactly("application/test+json");
        assertThat(mediaTypes.getConsumed()).containsExactly("text/plain");
        assertThat(EndpointMediaTypes.DEFAULT.getProduced()).contains("application/json");

        Link plainLink = new Link("https://example.test/actuator/health");
        Link templatedLink = new Link("https://example.test/actuator/health/{component}");
        assertThat(plainLink.getHref()).endsWith("/health");
        assertThat(plainLink.isTemplated()).isFalse();
        assertThat(templatedLink.isTemplated()).isTrue();

        WebEndpointResponse<String> response = new WebEndpointResponse<>("accepted", 202,
                MimeType.valueOf("text/plain"));
        assertThat(response.getBody()).isEqualTo("accepted");
        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(response.getContentType()).isEqualTo(MimeType.valueOf("text/plain"));

        EndpointLinksResolver linksResolver = new EndpointLinksResolver(
                List.of(new SimplePathMappedEndpoint("customEndpoint", "custom-path")), "/actuator");
        Map<String, Link> links = linksResolver.resolveLinks("https://example.test/actuator/");
        assertThat(links.get("self").getHref()).isEqualTo("https://example.test/actuator");
        assertThat(links.get("customendpoint").getHref()).isEqualTo("https://example.test/actuator/custom-path");
    }

    private static final class SimplePathMappedEndpoint implements ExposableEndpoint<Operation>, PathMappedEndpoint {

        private final EndpointId id;

        private final String rootPath;

        private SimplePathMappedEndpoint(String id, String rootPath) {
            this.id = EndpointId.of(id);
            this.rootPath = rootPath;
        }

        @Override
        public EndpointId getEndpointId() {
            return this.id;
        }

        @Override
        public boolean isEnableByDefault() {
            return true;
        }

        @Override
        public Collection<Operation> getOperations() {
            return Collections.emptyList();
        }

        @Override
        public String getRootPath() {
            return this.rootPath;
        }

    }

}
