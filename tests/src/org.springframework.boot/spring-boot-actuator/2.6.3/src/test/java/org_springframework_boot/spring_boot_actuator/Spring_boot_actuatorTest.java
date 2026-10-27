/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventsEndpoint;
import org.springframework.boot.actuate.audit.InMemoryAuditEventRepository;
import org.springframework.boot.actuate.beans.BeansEndpoint;
import org.springframework.boot.actuate.beans.BeansEndpoint.ApplicationBeans;
import org.springframework.boot.actuate.beans.BeansEndpoint.BeanDescriptor;
import org.springframework.boot.actuate.beans.BeansEndpoint.ContextBeans;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.InvocationContext;
import org.springframework.boot.actuate.endpoint.OperationType;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.Sanitizer;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver;
import org.springframework.boot.actuate.endpoint.web.EndpointMapping;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.Link;
import org.springframework.boot.actuate.endpoint.web.WebEndpointHttpMethod;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.WebOperation;
import org.springframework.boot.actuate.endpoint.web.WebOperationRequestPredicate;
import org.springframework.boot.actuate.env.EnvironmentEndpoint;
import org.springframework.boot.actuate.env.EnvironmentEndpoint.EnvironmentEntryDescriptor;
import org.springframework.boot.actuate.env.EnvironmentEndpoint.EnvironmentDescriptor;
import org.springframework.boot.actuate.env.EnvironmentEndpoint.PropertySourceDescriptor;
import org.springframework.boot.actuate.health.AdditionalHealthEndpointPath;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.DefaultHealthContributorRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.HttpCodeStatusMapper;
import org.springframework.boot.actuate.health.NamedContributor;
import org.springframework.boot.actuate.health.SimpleStatusAggregator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.health.StatusAggregator;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.trace.http.HttpTrace;
import org.springframework.boot.actuate.trace.http.HttpTraceEndpoint;
import org.springframework.boot.actuate.trace.http.InMemoryHttpTraceRepository;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_actuatorTest {

    @Test
    void healthBuilderAndStatusAggregatorPreserveDetailsAndSeverityOrder() {
        IllegalStateException failure = new IllegalStateException("database refused connection");

        Health health = Health.down(failure)
                .withDetail("database", "orders")
                .withDetails(Map.of("retryable", true, "attempts", 3))
                .build();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("database", "orders")
                .containsEntry("retryable", true)
                .containsEntry("attempts", 3)
                .containsKey("error");
        assertThat(new SimpleStatusAggregator("fatal", "down", "out-of-service", "unknown", "up")
                .getAggregateStatus(new Status("FATAL"), Status.UP, Status.OUT_OF_SERVICE))
                .isEqualTo(new Status("FATAL"));
        assertThat(StatusAggregator.getDefault().getAggregateStatus(Status.UP, Status.UNKNOWN)).isEqualTo(Status.UP);
    }

    @Test
    void compositeHealthContributorExposesNamedContributorsAndEndpointAggregatesNestedHealth() {
        Map<String, HealthContributor> nestedContributors = new LinkedHashMap<>();
        nestedContributors.put("cache", new FixedHealthIndicator(Health.up().withDetail("entries", 42).build()));
        nestedContributors.put("queue", new FixedHealthIndicator(Health.outOfService().build()));
        CompositeHealthContributor nested = CompositeHealthContributor.fromMap(nestedContributors);

        Map<String, HealthContributor> rootContributors = new LinkedHashMap<>();
        rootContributors.put("database", new FixedHealthIndicator(Health.down().withDetail("node", "primary").build()));
        rootContributors.put("nested", nested);
        DefaultHealthContributorRegistry registry = new DefaultHealthContributorRegistry(rootContributors);
        HealthEndpointGroups groups = HealthEndpointGroups.of(new VisibleHealthEndpointGroup(), Map.of());
        HealthEndpoint endpoint = new HealthEndpoint(registry, groups);

        assertThat(nested).extracting(NamedContributor::getName).containsExactly("cache", "queue");

        HealthComponent overallHealth = endpoint.health();
        assertThat(overallHealth.getStatus()).isEqualTo(Status.DOWN);
        assertThat(overallHealth).isInstanceOf(CompositeHealth.class);
        CompositeHealth compositeHealth = (CompositeHealth) overallHealth;
        assertThat(compositeHealth.getComponents()).containsOnlyKeys("database", "nested");
        assertThat(compositeHealth.getComponents().get("nested"))
                .isInstanceOf(CompositeHealth.class)
                .extracting(HealthComponent::getStatus)
                .isEqualTo(Status.OUT_OF_SERVICE);

        HealthComponent cacheHealth = endpoint.healthForPath("nested", "cache");
        assertThat(cacheHealth).isInstanceOf(Health.class);
        assertThat(((Health) cacheHealth).getDetails()).containsEntry("entries", 42);
    }

    @Test
    void healthContributorRegistryRegistersAndUnregistersRuntimeContributors() {
        DefaultHealthContributorRegistry registry = new DefaultHealthContributorRegistry();
        HealthIndicator initial = new FixedHealthIndicator(Health.up().build());
        HealthIndicator replacement = new FixedHealthIndicator(
                Health.unknown().withDetail("phase", "starting").build());

        registry.registerContributor("service", initial);
        HealthContributor removed = registry.unregisterContributor("service");
        registry.registerContributor("service", replacement);

        assertThat(removed).isSameAs(initial);
        assertThat(registry).extracting(NamedContributor::getName).containsExactly("service");
        HealthIndicator registered = (HealthIndicator) registry.iterator().next().getContributor();
        assertThat(registered.health().getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(registered.health().getDetails()).containsEntry("phase", "starting");
    }

    @Test
    void infoEndpointCombinesContributorDetailsInInsertionOrder() {
        InfoEndpoint endpoint = new InfoEndpoint(List.of(
                new MapInfoContributor(Map.of("build", Map.of("version", "test", "time", "2026-06-28"))),
                new MapInfoContributor(Map.of("git", Map.of("branch", "main")))));

        Map<String, Object> details = endpoint.info();
        Info info = new Info.Builder().withDetails(details).withDetail("runtime", "native-compatible").build();

        Object buildDetails = info.get("build");

        assertThat(details).containsOnlyKeys("build", "git");
        assertThat(buildDetails).isInstanceOf(Map.class);
        Map<?, ?> buildDetailsMap = (Map<?, ?>) buildDetails;
        assertThat(buildDetailsMap.get("version")).isEqualTo("test");
        assertThat(info.get("runtime")).isEqualTo("native-compatible");
        assertThat(info.getDetails()).containsEntry("git", Map.of("branch", "main"));
    }

    @Test
    void auditRepositoryAndEndpointFilterEventsByPrincipalTypeAndTime() {
        Instant start = Instant.parse("2026-06-28T10:00:00Z");
        InMemoryAuditEventRepository repository = new InMemoryAuditEventRepository();
        AuditEvent oldAliceLogin = new AuditEvent(start.minusSeconds(60), "alice", "AUTHENTICATION_SUCCESS",
                Map.of("remoteAddress", "10.0.0.1"));
        AuditEvent aliceLogin = new AuditEvent(start.plusSeconds(1), "alice", "AUTHENTICATION_SUCCESS",
                Map.of("remoteAddress", "10.0.0.2"));
        AuditEvent aliceLogout = new AuditEvent(start.plusSeconds(2), "alice", "LOGOUT", Map.of("session", "abc"));
        AuditEvent bobLogin = new AuditEvent(start.plusSeconds(3), "bob", "AUTHENTICATION_SUCCESS", Map.of());
        repository.add(oldAliceLogin);
        repository.add(aliceLogin);
        repository.add(aliceLogout);
        repository.add(bobLogin);

        AuditEventsEndpoint endpoint = new AuditEventsEndpoint(repository);
        OffsetDateTime afterStart = OffsetDateTime.ofInstant(start, ZoneOffset.UTC);

        assertThat(repository.find("alice", start, "AUTHENTICATION_SUCCESS"))
                .containsExactly(aliceLogin);
        assertThat(endpoint.events("alice", afterStart, null).getEvents())
                .containsExactlyInAnyOrder(aliceLogin, aliceLogout);
        assertThat(endpoint.events(null, afterStart, "AUTHENTICATION_SUCCESS").getEvents())
                .containsExactlyInAnyOrder(aliceLogin, bobLogin);
    }

    @Test
    void environmentEndpointReadsPropertySourcesAndSanitizesSensitiveValues() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("testProperties", Map.of(
                "app.name", "actuator-tests",
                "app.secret", "top-secret",
                "management.endpoint.health.show-details", "always")));
        EnvironmentEndpoint endpoint = new EnvironmentEndpoint(environment);

        EnvironmentEntryDescriptor secret = endpoint.environmentEntry("app.secret");
        EnvironmentEntryDescriptor appName = endpoint.environmentEntry("app.name");
        EnvironmentDescriptor descriptor = endpoint.environment("app.*");

        assertThat(secret.getProperty().getValue()).isEqualTo(SanitizableData.SANITIZED_VALUE);
        assertThat(appName.getProperty().getValue()).isEqualTo("actuator-tests");
        assertThat(descriptor.getPropertySources())
                .extracting(PropertySourceDescriptor::getName)
                .contains("testProperties");
    }

    @Test
    void httpTraceRepositoryAndEndpointExposeRecentRequestResponseDetails() {
        InMemoryHttpTraceRepository repository = new InMemoryHttpTraceRepository();
        repository.setCapacity(2);
        HttpTrace firstTrace = httpTrace("GET", "/actuator/health", 200, "alice", "session-1", 8L);
        HttpTrace secondTrace = httpTrace("POST", "/actuator/loggers", 204, "bob", "session-2", 12L);
        HttpTrace thirdTrace = httpTrace("GET", "/actuator/info", 200, "carol", "session-3", 5L);
        repository.add(firstTrace);
        repository.add(secondTrace);
        repository.add(thirdTrace);
        HttpTraceEndpoint endpoint = new HttpTraceEndpoint(repository);

        List<HttpTrace> traces = endpoint.traces().getTraces();
        HttpTrace latestTrace = traces.get(0);

        assertThat(traces).containsExactly(thirdTrace, secondTrace);
        assertThat(latestTrace.getTimestamp()).isEqualTo(Instant.parse("2026-06-28T10:00:00Z"));
        assertThat(latestTrace.getRequest().getMethod()).isEqualTo("GET");
        assertThat(latestTrace.getRequest().getUri()).isEqualTo(URI.create("https://example.test/actuator/info"));
        assertThat(latestTrace.getRequest().getHeaders()).containsEntry("Accept", List.of("application/json"));
        assertThat(latestTrace.getRequest().getRemoteAddress()).isEqualTo("192.0.2.10");
        assertThat(latestTrace.getResponse().getStatus()).isEqualTo(200);
        assertThat(latestTrace.getResponse().getHeaders()).containsEntry("Content-Type", List.of("application/json"));
        assertThat(latestTrace.getPrincipal().getName()).isEqualTo("carol");
        assertThat(latestTrace.getSession().getId()).isEqualTo("session-3");
        assertThat(latestTrace.getTimeTaken()).isEqualTo(5L);
    }

    @Test
    void beansEndpointDescribesContextBeansAliasesScopesAndDependencies() {
        GenericApplicationContext parent = new GenericApplicationContext();
        parent.setId("parent-context");
        parent.registerBean("parentRepository", SampleRepository.class, SampleRepository::new);
        parent.refresh();

        try {
            GenericApplicationContext context = new GenericApplicationContext();
            try {
                context.setId("child-context");
                context.setParent(parent);
                context.registerBean("repository", SampleRepository.class, SampleRepository::new);
                context.registerAlias("repository", "repoAlias");
                context.registerBean("sampleService", SampleService.class, SampleService::new,
                        beanDefinition -> beanDefinition.setDependsOn("repository"));
                context.refresh();

                ApplicationBeans applicationBeans = new BeansEndpoint(context).beans();

                assertThat(applicationBeans.getContexts()).containsKeys("child-context", "parent-context");

                ContextBeans childContext = applicationBeans.getContexts().get("child-context");
                BeanDescriptor repository = childContext.getBeans().get("repository");
                BeanDescriptor service = childContext.getBeans().get("sampleService");
                ContextBeans parentContext = applicationBeans.getContexts().get("parent-context");

                assertThat(childContext.getParentId()).isEqualTo("parent-context");
                assertThat(repository.getAliases()).containsExactly("repoAlias");
                assertThat(repository.getScope()).isEqualTo("singleton");
                assertThat(repository.getType()).isEqualTo(SampleRepository.class);
                assertThat(service.getDependencies()).containsExactly("repository");
                assertThat(parentContext.getBeans()).containsKey("parentRepository");
            } finally {
                context.close();
            }
        } finally {
            parent.close();
        }
    }

    @Test
    void sanitizerEndpointMappingLinksAndWebResponsesUsePublicEndpointContracts() {
        Sanitizer sanitizer = new Sanitizer("token", "credential");
        EndpointMapping mapping = new EndpointMapping("/actuator");
        EndpointLinksResolver linksResolver = new EndpointLinksResolver(List.of(
                new SimpleWebEndpoint("health", "health"),
                new SimpleWebEndpoint("custom", "custom/path")));
        Map<String, Link> links = linksResolver.resolveLinks("https://example.test" + mapping.getPath());
        WebEndpointResponse<Map<String, String>> response = new WebEndpointResponse<>(
                Map.of("status", "UP"), WebEndpointResponse.STATUS_OK, MimeTypeUtils.APPLICATION_JSON);

        assertThat(sanitizer.sanitize("api.token", "abc123")).isEqualTo(SanitizableData.SANITIZED_VALUE);
        assertThat(sanitizer.sanitize("display.name", "Actuator")).isEqualTo("Actuator");
        assertThat(mapping.createSubPath("health")).isEqualTo("/actuator/health");
        assertThat(links).containsKeys("health", "custom");
        assertThat(links.get("health").getHref()).isEqualTo("https://example.test/actuator/health");
        assertThat(links.get("custom").getHref()).isEqualTo("https://example.test/actuator/custom/path");
        assertThat(response.getBody()).containsEntry("status", "UP");
        assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
        assertThat(response.getContentType()).isEqualTo(MimeTypeUtils.APPLICATION_JSON);
    }

    private static HttpTrace httpTrace(String method, String path, int status, String principal, String sessionId,
            long timeTaken) {
        HttpTrace.Request request = new HttpTrace.Request(method, URI.create("https://example.test" + path),
                Map.of("Accept", List.of("application/json")), "192.0.2.10");
        HttpTrace.Response response = new HttpTrace.Response(status,
                Map.of("Content-Type", List.of("application/json")));
        return new HttpTrace(request, response, Instant.parse("2026-06-28T10:00:00Z"),
                new HttpTrace.Principal(principal), new HttpTrace.Session(sessionId), timeTaken);
    }

    private static final class SampleRepository {

    }

    private static final class SampleService {

    }

    private static final class FixedHealthIndicator implements HealthIndicator {

        private final Health health;

        private FixedHealthIndicator(Health health) {
            this.health = health;
        }

        @Override
        public Health health() {
            return this.health;
        }

    }

    private static final class VisibleHealthEndpointGroup implements HealthEndpointGroup {

        @Override
        public boolean isMember(String name) {
            return true;
        }

        @Override
        public boolean showComponents(SecurityContext securityContext) {
            return true;
        }

        @Override
        public boolean showDetails(SecurityContext securityContext) {
            return true;
        }

        @Override
        public StatusAggregator getStatusAggregator() {
            return StatusAggregator.getDefault();
        }

        @Override
        public HttpCodeStatusMapper getHttpCodeStatusMapper() {
            return HttpCodeStatusMapper.DEFAULT;
        }

        @Override
        public AdditionalHealthEndpointPath getAdditionalPath() {
            return null;
        }

    }

    private static final class MapInfoContributor implements InfoContributor {

        private final Map<String, Object> details;

        private MapInfoContributor(Map<String, Object> details) {
            this.details = details;
        }

        @Override
        public void contribute(Info.Builder builder) {
            builder.withDetails(this.details);
        }

    }

    private static final class SimpleWebOperation implements WebOperation {

        private final String id;

        private final WebOperationRequestPredicate requestPredicate;

        private SimpleWebOperation(String id, String path) {
            this.id = id;
            this.requestPredicate = new WebOperationRequestPredicate(path, WebEndpointHttpMethod.GET,
                    Collections.emptyList(), List.of(MimeTypeUtils.APPLICATION_JSON_VALUE));
        }

        @Override
        public String getId() {
            return this.id;
        }

        @Override
        public boolean isBlocking() {
            return true;
        }

        @Override
        public WebOperationRequestPredicate getRequestPredicate() {
            return this.requestPredicate;
        }

        @Override
        public OperationType getType() {
            return OperationType.READ;
        }

        @Override
        public Object invoke(InvocationContext context) {
            return Map.of("id", this.id);
        }

    }

    private static final class SimpleWebEndpoint implements ExposableWebEndpoint {

        private final EndpointId id;

        private final String rootPath;

        private SimpleWebEndpoint(String id, String rootPath) {
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
        public Collection<WebOperation> getOperations() {
            return List.of(new SimpleWebOperation(this.id.toLowerCaseString(), this.rootPath));
        }

        @Override
        public String getRootPath() {
            return this.rootPath;
        }

    }

}
