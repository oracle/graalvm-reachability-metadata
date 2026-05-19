/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_health;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.ApiVersion;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.Show;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.actuate.endpoint.AdditionalHealthEndpointPath;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointWebExtension;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.IndicatedHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.ReactiveHealthEndpointWebExtension;
import org.springframework.boot.health.actuate.endpoint.SimpleHttpCodeStatusMapper;
import org.springframework.boot.health.actuate.endpoint.SimpleStatusAggregator;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.actuate.endpoint.SystemHealthDescriptor;
import org.springframework.boot.health.application.AvailabilityStateHealthIndicator;
import org.springframework.boot.health.application.DiskSpaceHealthIndicator;
import org.springframework.boot.health.application.LivenessStateHealthIndicator;
import org.springframework.boot.health.application.ReadinessStateHealthIndicator;
import org.springframework.boot.health.application.SslHealthIndicator;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointProperties;
import org.springframework.boot.health.autoconfigure.application.DiskSpaceHealthIndicatorProperties;
import org.springframework.boot.health.autoconfigure.application.SslHealthIndicatorProperties;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.AbstractReactiveHealthIndicator;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.CompositeReactiveHealthContributor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.PingHealthIndicator;
import org.springframework.boot.health.contributor.ReactiveHealthContributor;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.DefaultHealthContributorRegistry;
import org.springframework.boot.health.registry.DefaultReactiveHealthContributorRegistry;
import org.springframework.boot.health.registry.HealthContributorNameValidator;
import org.springframework.boot.info.SslInfo;
import org.springframework.boot.info.SslInfo.CertificateChainInfo;
import org.springframework.boot.info.SslInfo.CertificateInfo;
import org.springframework.boot.info.SslInfo.CertificateValidityInfo;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.util.unit.DataSize;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class Spring_boot_healthTest {

    @Test
    void healthBuilderCreatesImmutableHealthWithStatusAndDetails() {
        Status degraded = new Status("DEGRADED", "service is responding slowly");
        Health health = Health.status(degraded)
            .withDetail("node", "alpha")
            .withDetails(Map.of("replicas", 2))
            .down(new IllegalStateException("database unavailable"))
            .build();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("node", "alpha").containsEntry("replicas", 2);
        assertThat((String) health.getDetails().get("error"))
            .contains(IllegalStateException.class.getName(), "database unavailable");
        assertThat(health.toString()).contains("DOWN", "node");
        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> health.getDetails().put("mutable", true));

        assertThat(degraded.getCode()).isEqualTo("DEGRADED");
        assertThat(degraded.getDescription()).isEqualTo("service is responding slowly");
        assertThat(degraded).isEqualTo(new Status("DEGRADED"));
        assertThat(degraded).hasToString("DEGRADED");
    }

    @Test
    void healthIndicatorsReportSuccessfulAndFailedChecks() {
        Health ping = new PingHealthIndicator().health();
        Health failed = new FailingHealthIndicator().health();

        assertThat(ping.getStatus()).isEqualTo(Status.UP);
        assertThat(failed.getStatus()).isEqualTo(Status.DOWN);
        assertThat((String) failed.getDetails().get("error"))
            .contains(IllegalStateException.class.getName(), "simulated failure");

        HealthIndicator detailedIndicator = () -> Health.up().withDetail("secret", "value").build();
        Health withoutDetails = detailedIndicator.health(false);
        assertThat(withoutDetails.getStatus()).isEqualTo(Status.UP);
        assertThat(withoutDetails.getDetails()).isEmpty();
    }

    @Test
    void compositeContributorsAndRegistryExposeNamedContributors() {
        HealthIndicator database = () -> Health.up().withDetail("kind", "database").build();
        HealthIndicator broker = () -> Health.outOfService().build();
        Map<String, HealthContributor> children = new LinkedHashMap<>();
        children.put("database", database);
        children.put("broker", broker);
        CompositeHealthContributor composite = CompositeHealthContributor.fromMap(children);

        assertThat(composite.getContributor("database")).isSameAs(database);
        assertThat(composite.stream().map((entry) -> entry.name())).containsExactly("database", "broker");

        HealthContributorNameValidator noNestedNames = (name) -> {
            if (name.contains("/")) {
                throw new IllegalStateException("nested names are not accepted");
            }
        };
        DefaultHealthContributorRegistry registry = new DefaultHealthContributorRegistry(Set.of(noNestedNames),
            (registrations) -> registrations.accept("system", composite));
        registry.registerContributor("ping", new PingHealthIndicator());

        assertThat(registry.getContributor("system")).isSameAs(composite);
        assertThat(registry.stream().map((entry) -> entry.name())).containsExactly("system", "ping");
        assertThat(registry.unregisterContributor("ping")).isInstanceOf(PingHealthIndicator.class);
        assertThat(registry.getContributor("ping")).isNull();
        assertThatIllegalStateException().isThrownBy(() -> registry.registerContributor("system", database))
            .withMessageContaining("already been registered");
        assertThatIllegalStateException().isThrownBy(() -> registry.registerContributor("bad/name", database))
            .withMessageContaining("nested names are not accepted");
    }

    @Test
    void healthEndpointAggregatesContributorsGroupsAndNestedPaths() {
        DefaultHealthContributorRegistry registry = endpointRegistry();
        HealthEndpointGroup primary = new TestHealthEndpointGroup((name) -> true, true, true,
            new SimpleStatusAggregator(), new SimpleHttpCodeStatusMapper(), null);
        HealthEndpointGroup readiness = new TestHealthEndpointGroup((name) -> name.equals("database"), true, false,
            new SimpleStatusAggregator(), new SimpleHttpCodeStatusMapper(),
            AdditionalHealthEndpointPath.of(WebServerNamespace.MANAGEMENT, "readyz"));
        HealthEndpointGroups groups = HealthEndpointGroups.of(primary, Map.of("readiness", readiness));
        HealthEndpoint endpoint = new HealthEndpoint(registry, null, groups, Duration.ofSeconds(1));

        HealthDescriptor descriptor = endpoint.health();
        assertThat(descriptor).isInstanceOf(SystemHealthDescriptor.class);
        SystemHealthDescriptor system = (SystemHealthDescriptor) descriptor;
        assertThat(system.getStatus()).isEqualTo(Status.DOWN);
        assertThat(system.getGroups()).containsExactly("readiness");
        assertThat(system.getComponents()).containsOnlyKeys("cache", "database", "external");

        HealthDescriptor database = endpoint.healthForPath("database");
        assertThat(database).isInstanceOf(IndicatedHealthDescriptor.class);
        assertThat(database.getStatus()).isEqualTo(Status.DOWN);
        assertThat(((IndicatedHealthDescriptor) database).getDetails()).containsEntry("database", "primary");

        HealthDescriptor nested = endpoint.healthForPath("external", "ping");
        assertThat(nested).isInstanceOf(IndicatedHealthDescriptor.class);
        assertThat(nested.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);

        HealthDescriptor readinessDescriptor = endpoint.healthForPath("readiness");
        assertThat(readinessDescriptor).isInstanceOf(CompositeHealthDescriptor.class);
        CompositeHealthDescriptor readinessComposite = (CompositeHealthDescriptor) readinessDescriptor;
        assertThat(readinessComposite.getStatus()).isEqualTo(Status.DOWN);
        assertThat(readinessComposite.getComponents()).containsOnlyKeys("database");
        IndicatedHealthDescriptor readinessDatabase =
            (IndicatedHealthDescriptor) readinessComposite.getComponents().get("database");
        assertThat(readinessDatabase.getDetails()).containsEntry("database", "primary");

        assertThat(endpoint.healthForPath("missing")).isNull();
        assertThat(groups.get(AdditionalHealthEndpointPath.from("management:/readyz"))).isSameAs(readiness);
        assertThat(groups.getAllWithAdditionalPath(WebServerNamespace.MANAGEMENT)).containsExactly(readiness);
    }

    @Test
    void webExtensionUsesHttpStatusMappingsAndVisibilityRules() {
        DefaultHealthContributorRegistry registry = endpointRegistry();
        HealthEndpointGroup primary = new TestHealthEndpointGroup((name) -> true, false, false,
            new SimpleStatusAggregator(), new SimpleHttpCodeStatusMapper(), null);
        HealthEndpointWebExtension extension = new HealthEndpointWebExtension(registry, null,
            HealthEndpointGroups.of(primary, Map.of()), null);

        WebEndpointResponse<HealthDescriptor> rootResponse = extension.health(ApiVersion.V3, WebServerNamespace.SERVER,
            SecurityContext.NONE);
        assertThat(rootResponse.getStatus()).isEqualTo(WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE);
        assertThat(rootResponse.getBody()).isInstanceOf(CompositeHealthDescriptor.class);
        assertThat(((CompositeHealthDescriptor) rootResponse.getBody()).getComponents()).isNull();

        WebEndpointResponse<HealthDescriptor> hiddenComponent = extension.health(ApiVersion.V3,
            WebServerNamespace.SERVER, SecurityContext.NONE, "database");
        assertThat(hiddenComponent.getStatus()).isEqualTo(WebEndpointResponse.STATUS_NOT_FOUND);
        assertThat(hiddenComponent.getBody()).isNull();

        WebEndpointResponse<HealthDescriptor> shownComponent = extension.health(ApiVersion.V3,
            WebServerNamespace.SERVER, SecurityContext.NONE, true, "database");
        assertThat(shownComponent.getStatus()).isEqualTo(WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE);
        assertThat(shownComponent.getBody()).isInstanceOf(IndicatedHealthDescriptor.class);
        IndicatedHealthDescriptor shownDatabase = (IndicatedHealthDescriptor) shownComponent.getBody();
        assertThat(shownDatabase.getDetails()).containsEntry("database", "primary");
    }

    @Test
    void statusAggregatorsHttpMappersAndAdditionalPathsHandleCustomValues() {
        Status fatal = new Status("FATAL");
        SimpleStatusAggregator aggregator = new SimpleStatusAggregator("FATAL", "DOWN", "OUT_OF_SERVICE", "UNKNOWN",
            "UP");
        HttpCodeStatusMapper mapper = new SimpleHttpCodeStatusMapper(Map.of("FATAL", 599));
        AdditionalHealthEndpointPath parsed = AdditionalHealthEndpointPath.from("management:/livez");
        AdditionalHealthEndpointPath equivalent = AdditionalHealthEndpointPath.of(WebServerNamespace.MANAGEMENT,
            "livez");

        assertThat(new SimpleStatusAggregator().getAggregateStatus(Set.of(Status.UP, Status.DOWN)))
            .isEqualTo(Status.DOWN);
        assertThat(aggregator.getAggregateStatus(Set.of(Status.UP, fatal))).isEqualTo(fatal);
        assertThat(new SimpleHttpCodeStatusMapper().getStatusCode(Status.OUT_OF_SERVICE))
            .isEqualTo(WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE);
        assertThat(mapper.getStatusCode(fatal)).isEqualTo(599);
        assertThat(parsed).isEqualTo(equivalent);
        assertThat(parsed.hasNamespace(WebServerNamespace.MANAGEMENT)).isTrue();
        assertThat(parsed.getNamespace()).isEqualTo(WebServerNamespace.MANAGEMENT);
        assertThatIllegalArgumentException().isThrownBy(() -> AdditionalHealthEndpointPath.from("server:/nested/path"))
            .withMessageContaining("only one segment");
    }

    @Test
    void applicationHealthIndicatorsExposeDiskSpaceAndAvailabilityStates() {
        Health diskSpace = new DiskSpaceHealthIndicator(new File("."), DataSize.ofBytes(0)).health();
        FixedApplicationAvailability availability = new FixedApplicationAvailability(LivenessState.CORRECT,
            ReadinessState.REFUSING_TRAFFIC);
        AvailabilityStateHealthIndicator readinessWithDefault = new AvailabilityStateHealthIndicator(availability,
            ReadinessState.class, (mappings) -> mappings.addDefaultStatus(Status.UNKNOWN));

        assertThat(diskSpace.getStatus()).isEqualTo(Status.UP);
        assertThat(diskSpace.getDetails()).containsKeys("total", "free", "threshold", "path", "exists");
        assertThat(new LivenessStateHealthIndicator(availability).health().getStatus()).isEqualTo(Status.UP);
        assertThat(new ReadinessStateHealthIndicator(availability).health().getStatus())
            .isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(readinessWithDefault.health().getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void sslHealthIndicatorReportsInvalidCertificateChains() throws Exception {
        KeyStore keyStore = keyStoreWithCertificate("server", expiredCertificate());
        DefaultSslBundleRegistry sslBundles = new DefaultSslBundleRegistry("web", SslBundle.of(
            SslStoreBundle.of(keyStore, null, null)));
        SslInfo sslInfo = new SslInfo(sslBundles, Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC));
        SslHealthIndicator indicator = new SslHealthIndicator(sslInfo, Duration.ofDays(30));

        Health health = indicator.health();
        List<?> invalidChains = (List<?>) health.getDetails().get("invalidChains");
        List<?> validChains = (List<?>) health.getDetails().get("validChains");
        List<?> expiringChains = (List<?>) health.getDetails().get("expiringChains");

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(validChains).isEmpty();
        assertThat(expiringChains).isEmpty();
        assertThat(invalidChains).hasSize(1);
        CertificateChainInfo invalidChain = (CertificateChainInfo) invalidChains.get(0);
        assertThat(invalidChain.getAlias()).isEqualTo("server");
        assertThat(invalidChain.getCertificates()).hasSize(1);
        CertificateInfo certificate = invalidChain.getCertificates().get(0);
        assertThat(certificate.getSubject()).contains("expired.example");
        assertThat(certificate.getIssuer()).contains("expired.example");
        CertificateValidityInfo validity = certificate.getValidity();
        assertThat(validity.getStatus()).isEqualTo(CertificateValidityInfo.Status.EXPIRED);
        assertThat(validity.getMessage()).contains("Not valid after");
        assertThat(sslInfo.getBundle("web").getCertificateChains()).extracting(CertificateChainInfo::getAlias)
            .containsExactly("server");
    }

    @Test
    void reactiveHealthContributorsAdaptAndExposeWebResponses() {
        ReactiveHealthIndicator search = () -> Mono.just(Health.up().withDetail("index", "ready").build());
        ReactiveFailingHealthIndicator queue = new ReactiveFailingHealthIndicator();
        Map<String, ReactiveHealthContributor> children = new LinkedHashMap<>();
        children.put("search", search);
        children.put("queue", queue);
        CompositeReactiveHealthContributor services = CompositeReactiveHealthContributor.fromMap(children);
        DefaultReactiveHealthContributorRegistry registry = new DefaultReactiveHealthContributorRegistry();
        registry.registerContributor("services", services);
        HealthEndpointGroup primary = new TestHealthEndpointGroup((name) -> true, true, true,
            new SimpleStatusAggregator(), new SimpleHttpCodeStatusMapper(), null);
        ReactiveHealthEndpointWebExtension extension = new ReactiveHealthEndpointWebExtension(registry,
            new DefaultHealthContributorRegistry(), HealthEndpointGroups.of(primary, Map.of()), Duration.ofSeconds(1));

        assertThat(registry.getContributor("services")).isSameAs(services);
        CompositeHealthContributor blockingServices = services.asHealthContributor();
        HealthContributor blockingSearch = blockingServices.getContributor("search");
        assertThat(blockingSearch).isInstanceOf(HealthIndicator.class);
        assertThat(((HealthIndicator) blockingSearch).health(false).getDetails()).isEmpty();
        Health failed = queue.health().block(Duration.ofSeconds(5));
        assertThat(failed).isNotNull();
        assertThat(failed.getStatus()).isEqualTo(Status.DOWN);
        assertThat((String) failed.getDetails().get("error"))
            .contains(IllegalStateException.class.getName(), "reactive backend unavailable");

        WebEndpointResponse<? extends HealthDescriptor> rootResponse = extension.health(ApiVersion.V3,
            WebServerNamespace.SERVER, SecurityContext.NONE).block(Duration.ofSeconds(5));
        assertThat(rootResponse).isNotNull();
        assertThat(rootResponse.getStatus()).isEqualTo(WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE);
        assertThat(rootResponse.getBody()).isInstanceOf(CompositeHealthDescriptor.class);
        CompositeHealthDescriptor root = (CompositeHealthDescriptor) rootResponse.getBody();
        assertThat(root.getComponents()).containsOnlyKeys("services");

        WebEndpointResponse<? extends HealthDescriptor> queueResponse = extension.health(ApiVersion.V3,
            WebServerNamespace.SERVER, SecurityContext.NONE, "services", "queue").block(Duration.ofSeconds(5));
        assertThat(queueResponse).isNotNull();
        assertThat(queueResponse.getStatus()).isEqualTo(WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE);
        assertThat(queueResponse.getBody()).isInstanceOf(IndicatedHealthDescriptor.class);
        IndicatedHealthDescriptor queueDescriptor = (IndicatedHealthDescriptor) queueResponse.getBody();
        assertThat(queueDescriptor.getStatus()).isEqualTo(Status.DOWN);
        assertThat((String) queueDescriptor.getDetails().get("error"))
            .contains(IllegalStateException.class.getName(), "reactive backend unavailable");
    }

    @Test
    void healthConfigurationPropertiesAreMutableValueObjects() {
        DiskSpaceHealthIndicatorProperties diskSpace = new DiskSpaceHealthIndicatorProperties();
        diskSpace.setPath(new File("."));
        diskSpace.setThreshold(DataSize.ofMegabytes(512));
        SslHealthIndicatorProperties ssl = new SslHealthIndicatorProperties();
        ssl.setCertificateValidityWarningThreshold(Duration.ofDays(14));
        HealthEndpointProperties endpoint = new HealthEndpointProperties();
        endpoint.setShowDetails(Show.ALWAYS);
        endpoint.setShowComponents(Show.WHEN_AUTHORIZED);
        endpoint.setRoles(Set.of("ACTUATOR"));
        endpoint.getStatus().setOrder(List.of("fatal", "down", "out-of-service", "unknown", "up"));
        endpoint.getStatus().getHttpMapping().put("fatal", 599);
        HealthEndpointProperties.Group group = new HealthEndpointProperties.Group();
        group.setInclude(Set.of("database", "diskSpace"));
        group.setExclude(Set.of("experimental"));
        group.setShowDetails(Show.NEVER);
        group.setAdditionalPath("management:/readyz");
        endpoint.getGroup().put("readiness", group);
        endpoint.getLogging().setSlowIndicatorThreshold(Duration.ofMillis(250));

        assertThat(diskSpace.getPath()).isEqualTo(new File("."));
        assertThat(diskSpace.getThreshold()).isEqualTo(DataSize.ofMegabytes(512));
        assertThat(ssl.getCertificateValidityWarningThreshold()).isEqualTo(Duration.ofDays(14));
        assertThat(endpoint.getShowDetails()).isEqualTo(Show.ALWAYS);
        assertThat(endpoint.getShowComponents()).isEqualTo(Show.WHEN_AUTHORIZED);
        assertThat(endpoint.getRoles()).containsExactly("ACTUATOR");
        assertThat(endpoint.getStatus().getOrder()).containsExactly("fatal", "down", "out-of-service", "unknown", "up");
        assertThat(endpoint.getStatus().getHttpMapping()).containsEntry("fatal", 599);
        assertThat(endpoint.getGroup().get("readiness").getInclude())
            .containsExactlyInAnyOrder("database", "diskSpace");
        assertThat(endpoint.getGroup().get("readiness").getExclude()).containsExactly("experimental");
        assertThat(endpoint.getGroup().get("readiness").getShowDetails()).isEqualTo(Show.NEVER);
        assertThat(endpoint.getGroup().get("readiness").getAdditionalPath()).isEqualTo("management:/readyz");
        assertThat(endpoint.getLogging().getSlowIndicatorThreshold()).isEqualTo(Duration.ofMillis(250));
    }

    private static KeyStore keyStoreWithCertificate(String alias, Certificate certificate) throws Exception {
        KeyStore keyStore = new TestKeyStore(Map.of(alias, new Certificate[] { certificate }));
        keyStore.load(null, null);
        return keyStore;
    }

    private static Certificate expiredCertificate() throws CertificateException {
        String pem = """
            -----BEGIN CERTIFICATE-----
            MIIDFTCCAf2gAwIBAgIUVVj8cnxm1PkljT2U68Qpoy8Lg4YwDQYJKoZIhvcNAQEL
            BQAwGjEYMBYGA1UEAwwPZXhwaXJlZC5leGFtcGxlMB4XDTI2MDUxOTE4MDIwM1oX
            DTI2MDUyMDE4MDIwM1owGjEYMBYGA1UEAwwPZXhwaXJlZC5leGFtcGxlMIIBIjAN
            BgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArtg7n/9CjqDRr6EupshrlQeLVp3L
            f3XOO3mXbyjgcCD1PdSteqt9MW4aHLcpmMF+Gy3L4RNZeHZ5HdUybrYgE6RoL/7d
            Du3CpJzXQi6w3EqGklqeZA05fEvhazgLP5IP7GLsQ4eoHYYyjA67o4PrmjqghmOh
            7tKcFvYILuiLU6woG4BVPKAQowOgStNXz1SHPwJBUTKkfS5gIkjTc4mTfG0+j9+S
            8c7AiO8fCHHzvQJThiynDeo65SiHLx/jJwn4GTNen8oY7JrL5/QOrQ1wV/HKoYrg
            rTzGBxmV4tco/iujjx4i4AdpaI5zGOG+X2Q8bOB38+RU50aJcHeks/RAZQIDAQAB
            o1MwUTAdBgNVHQ4EFgQUEronZIfgpGz29nLD+EUFyQqZlygwHwYDVR0jBBgwFoAU
            EronZIfgpGz29nLD+EUFyQqZlygwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0B
            AQsFAAOCAQEAE37f7sOtfeNRpt6oc4XQS1E+QCs34MHoiBcE7Yo5GTgETL4ZLJlg
            UhQOq3mtT3/hjzpR71GFx+pYZ98SGXE8A8arc7XIPkJNhZ9awrJhGQl0zl3qpIG6
            WI0GTfD49qe6QEFfxiDyA+sDAQEx60YDMlrJaurmxKu7Pa2gIbVuArbuGxsCyKAz
            pZd+j5HP1cEjurgyiuKv2DvH0eKXH6xa/7W5rvvJkklFg+XXeJgu/pfflHNqRH85
            bUzWXuG+eD2jBpjwVr7xELLSCH7cwZZtNmlGbzExg9eSpsCD2PpU/KKjL2hfmze8
            oAN2vblsL1HlfMPn5ICwHDxNE5D05bJ/8A==
            -----END CERTIFICATE-----
            """;
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        byte[] bytes = pem.getBytes(StandardCharsets.US_ASCII);
        return certificateFactory.generateCertificate(new ByteArrayInputStream(bytes));
    }

    private static DefaultHealthContributorRegistry endpointRegistry() {
        DefaultHealthContributorRegistry registry = new DefaultHealthContributorRegistry();
        registry.registerContributor("database", (HealthIndicator) () -> Health.down()
            .withDetail("database", "primary")
            .build());
        registry.registerContributor("cache", (HealthIndicator) () -> Health.up().withDetail("cache", "local").build());
        Map<String, HealthContributor> external = new LinkedHashMap<>();
        external.put("ping", (HealthIndicator) () -> Health.outOfService().withDetail("remote", "api").build());
        registry.registerContributor("external", CompositeHealthContributor.fromMap(external));
        return registry;
    }

    private record TestHealthEndpointGroup(Predicate<String> membership, boolean showComponents, boolean showDetails,
            StatusAggregator statusAggregator, HttpCodeStatusMapper httpCodeStatusMapper,
            AdditionalHealthEndpointPath additionalPath) implements HealthEndpointGroup {

        @Override
        public boolean isMember(String name) {
            return this.membership.test(name);
        }

        @Override
        public boolean showComponents(SecurityContext securityContext) {
            return this.showComponents;
        }

        @Override
        public boolean showDetails(SecurityContext securityContext) {
            return this.showDetails;
        }

        @Override
        public StatusAggregator getStatusAggregator() {
            return this.statusAggregator;
        }

        @Override
        public HttpCodeStatusMapper getHttpCodeStatusMapper() {
            return this.httpCodeStatusMapper;
        }

        @Override
        public AdditionalHealthEndpointPath getAdditionalPath() {
            return this.additionalPath;
        }

    }

    private static final class TestKeyStore extends KeyStore {

        private TestKeyStore(Map<String, Certificate[]> certificateChains) {
            super(new TestKeyStoreSpi(certificateChains), new TestProvider(), "test");
        }

    }

    private static final class TestProvider extends Provider {

        private static final long serialVersionUID = 1L;

        private TestProvider() {
            super("test", "1", "test");
        }

    }

    private static final class TestKeyStoreSpi extends KeyStoreSpi {

        private final Map<String, Certificate[]> certificateChains;

        private TestKeyStoreSpi(Map<String, Certificate[]> certificateChains) {
            this.certificateChains = certificateChains;
        }

        @Override
        public Key engineGetKey(String alias, char[] password)
                throws NoSuchAlgorithmException, UnrecoverableKeyException {
            return null;
        }

        @Override
        public Certificate[] engineGetCertificateChain(String alias) {
            return this.certificateChains.get(alias);
        }

        @Override
        public Certificate engineGetCertificate(String alias) {
            Certificate[] certificates = this.certificateChains.get(alias);
            return (certificates != null && certificates.length > 0) ? certificates[0] : null;
        }

        @Override
        public Date engineGetCreationDate(String alias) {
            return new Date(0);
        }

        @Override
        public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain)
                throws KeyStoreException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void engineDeleteEntry(String alias) throws KeyStoreException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Enumeration<String> engineAliases() {
            return Collections.enumeration(this.certificateChains.keySet());
        }

        @Override
        public boolean engineContainsAlias(String alias) {
            return this.certificateChains.containsKey(alias);
        }

        @Override
        public int engineSize() {
            return this.certificateChains.size();
        }

        @Override
        public boolean engineIsKeyEntry(String alias) {
            return this.certificateChains.containsKey(alias);
        }

        @Override
        public boolean engineIsCertificateEntry(String alias) {
            return false;
        }

        @Override
        public String engineGetCertificateAlias(Certificate cert) {
            return this.certificateChains.entrySet()
                .stream()
                .filter((entry) -> List.of(entry.getValue()).contains(cert))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        }

        @Override
        public void engineStore(OutputStream stream, char[] password)
                throws IOException, NoSuchAlgorithmException, CertificateException {
            // This in-memory key store is read-only and has no external representation.
        }

        @Override
        public void engineLoad(InputStream stream, char[] password)
                throws IOException, NoSuchAlgorithmException, CertificateException {
            // The certificate chains are supplied directly when the key store is created.
        }

    }

    private static final class FailingHealthIndicator extends AbstractHealthIndicator {

        @Override
        protected void doHealthCheck(Health.Builder builder) {
            throw new IllegalStateException("simulated failure");
        }

    }

    private static final class ReactiveFailingHealthIndicator extends AbstractReactiveHealthIndicator {

        @Override
        protected Mono<Health> doHealthCheck(Health.Builder builder) {
            return Mono.error(new IllegalStateException("reactive backend unavailable"));
        }

    }

    private static final class FixedApplicationAvailability implements ApplicationAvailability {

        private final LivenessState livenessState;

        private final ReadinessState readinessState;

        private FixedApplicationAvailability(LivenessState livenessState, ReadinessState readinessState) {
            this.livenessState = livenessState;
            this.readinessState = readinessState;
        }

        @Override
        public LivenessState getLivenessState() {
            return this.livenessState;
        }

        @Override
        public ReadinessState getReadinessState() {
            return this.readinessState;
        }

        @Override
        public <S extends AvailabilityState> S getState(Class<S> stateType, S defaultState) {
            S state = getState(stateType);
            return (state != null) ? state : defaultState;
        }

        @Override
        public <S extends AvailabilityState> S getState(Class<S> stateType) {
            if (stateType == LivenessState.class) {
                return stateType.cast(this.livenessState);
            }
            if (stateType == ReadinessState.class) {
                return stateType.cast(this.readinessState);
            }
            return null;
        }

        @Override
        public <S extends AvailabilityState> AvailabilityChangeEvent<S> getLastChangeEvent(Class<S> stateType) {
            return null;
        }

    }

}
