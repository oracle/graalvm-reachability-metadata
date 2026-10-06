/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_couchbase;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.couchbase.client.core.env.Authenticator;
import com.couchbase.client.core.env.PasswordAuthenticator;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.env.ClusterEnvironment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.couchbase.autoconfigure.ClusterEnvironmentBuilderCustomizer;
import org.springframework.boot.couchbase.autoconfigure.CouchbaseAutoConfiguration;
import org.springframework.boot.couchbase.autoconfigure.CouchbaseConnectionDetails;
import org.springframework.boot.couchbase.autoconfigure.CouchbaseProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_couchbaseTest {

    @Test
    void autoConfigurationIsAdvertisedForSpringBootDiscovery() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates()).contains(CouchbaseAutoConfiguration.class.getName());
    }

    @Test
    void binderConfiguresConnectionAuthenticationIoSslAndTimeoutProperties() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource();
        source.put("spring.couchbase.connection-string", "couchbase://database.example.test");
        source.put("spring.couchbase.username", "application");
        source.put("spring.couchbase.password", "secret");
        source.put("spring.couchbase.authentication.pem.certificates", "certificate-data");
        source.put("spring.couchbase.authentication.pem.private-key", "private-key-data");
        source.put("spring.couchbase.authentication.pem.private-key-password", "key-secret");
        source.put("spring.couchbase.authentication.jks.location", "file:/certificates/client.jks");
        source.put("spring.couchbase.authentication.jks.password", "store-secret");
        source.put("spring.couchbase.env.io.min-endpoints", "2");
        source.put("spring.couchbase.env.io.max-endpoints", "8");
        source.put("spring.couchbase.env.io.idle-http-connection-timeout", "10s");
        source.put("spring.couchbase.env.ssl.bundle", "client");
        source.put("spring.couchbase.env.timeouts.connect", "10s");
        source.put("spring.couchbase.env.timeouts.disconnect", "11s");
        source.put("spring.couchbase.env.timeouts.key-value", "12s");
        source.put("spring.couchbase.env.timeouts.key-value-durable", "13s");
        source.put("spring.couchbase.env.timeouts.query", "14s");
        source.put("spring.couchbase.env.timeouts.view", "15s");
        source.put("spring.couchbase.env.timeouts.search", "16s");
        source.put("spring.couchbase.env.timeouts.analytics", "17s");
        source.put("spring.couchbase.env.timeouts.management", "18s");
        CouchbaseProperties properties = new CouchbaseProperties();

        CouchbaseProperties bound = new Binder(source)
                .bind("spring.couchbase", Bindable.ofInstance(properties))
                .orElseThrow(() -> new AssertionError("Couchbase properties were not bound"));

        assertThat(bound).isSameAs(properties);
        assertThat(properties.getConnectionString()).isEqualTo("couchbase://database.example.test");
        assertThat(properties.getUsername()).isEqualTo("application");
        assertThat(properties.getPassword()).isEqualTo("secret");
        assertThat(properties.getAuthentication().getPem().getCertificates()).isEqualTo("certificate-data");
        assertThat(properties.getAuthentication().getPem().getPrivateKey()).isEqualTo("private-key-data");
        assertThat(properties.getAuthentication().getPem().getPrivateKeyPassword()).isEqualTo("key-secret");
        assertThat(properties.getAuthentication().getJks().getLocation())
                .isEqualTo("file:/certificates/client.jks");
        assertThat(properties.getAuthentication().getJks().getPassword()).isEqualTo("store-secret");
        assertThat(properties.getEnv().getIo().getMinEndpoints()).isEqualTo(2);
        assertThat(properties.getEnv().getIo().getMaxEndpoints()).isEqualTo(8);
        assertThat(properties.getEnv().getIo().getIdleHttpConnectionTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getEnv().getSsl().getEnabled()).isTrue();
        assertThat(properties.getEnv().getSsl().getBundle()).isEqualTo("client");
        assertThat(properties.getEnv().getTimeouts().getConnect()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getEnv().getTimeouts().getDisconnect()).isEqualTo(Duration.ofSeconds(11));
        assertThat(properties.getEnv().getTimeouts().getKeyValue()).isEqualTo(Duration.ofSeconds(12));
        assertThat(properties.getEnv().getTimeouts().getKeyValueDurable()).isEqualTo(Duration.ofSeconds(13));
        assertThat(properties.getEnv().getTimeouts().getQuery()).isEqualTo(Duration.ofSeconds(14));
        assertThat(properties.getEnv().getTimeouts().getView()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.getEnv().getTimeouts().getSearch()).isEqualTo(Duration.ofSeconds(16));
        assertThat(properties.getEnv().getTimeouts().getAnalytics()).isEqualTo(Duration.ofSeconds(17));
        assertThat(properties.getEnv().getTimeouts().getManagement()).isEqualTo(Duration.ofSeconds(18));
    }

    @Test
    void autoConfigurationCreatesAndCustomizesAClientFromBoundProperties() {
        AtomicBoolean customizerInvoked = new AtomicBoolean();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource("couchbase-test", Map.ofEntries(
                            Map.entry("spring.couchbase.connection-string", "couchbase://127.0.0.1"),
                            Map.entry("spring.couchbase.username", "application"),
                            Map.entry("spring.couchbase.password", "secret"),
                            Map.entry("spring.couchbase.env.io.idle-http-connection-timeout", "10s"),
                            Map.entry("spring.couchbase.env.timeouts.connect", "10s"),
                            Map.entry("spring.couchbase.env.timeouts.disconnect", "10s"),
                            Map.entry("spring.couchbase.env.timeouts.key-value", "10s"),
                            Map.entry("spring.couchbase.env.timeouts.key-value-durable", "10s"),
                            Map.entry("spring.couchbase.env.timeouts.query", "10s"),
                            Map.entry("spring.couchbase.env.timeouts.view", "10s"),
                            Map.entry("spring.couchbase.env.timeouts.search", "10s"),
                            Map.entry("spring.couchbase.env.timeouts.analytics", "10s"),
                            Map.entry("spring.couchbase.env.timeouts.management", "10s"))));
            context.registerBean(ClusterEnvironmentBuilderCustomizer.class,
                    () -> (builder) -> customizerInvoked.set(true));
            context.register(CouchbaseAutoConfiguration.class);
            context.refresh();

            CouchbaseProperties properties = context.getBean(CouchbaseProperties.class);
            CouchbaseConnectionDetails details = context.getBean(CouchbaseConnectionDetails.class);

            assertThat(properties.getConnectionString()).isEqualTo("couchbase://127.0.0.1");
            assertThat(properties.getUsername()).isEqualTo("application");
            assertThat(properties.getPassword()).isEqualTo("secret");
            assertThat(details.getConnectionString()).isEqualTo("couchbase://127.0.0.1");
            assertThat(details.getUsername()).isEqualTo("application");
            assertThat(details.getPassword()).isEqualTo("secret");
            assertThat(details.getSslBundle()).isNull();
            assertThat(context.getBean(Authenticator.class)).isInstanceOf(PasswordAuthenticator.class);
            assertThat(context.getBean(ClusterEnvironment.class)).isNotNull();
            assertThat(context.getBean(Cluster.class)).isNotNull();
            assertThat(customizerInvoked).isTrue();
        }
    }

    @Test
    void autoConfigurationUsesUserProvidedConnectionDetailsWithoutProperties() {
        TestCouchbaseConnectionDetails connectionDetails = new TestCouchbaseConnectionDetails();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CouchbaseConnectionDetails.class, () -> connectionDetails);
            context.register(CouchbaseAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBean(CouchbaseConnectionDetails.class)).isSameAs(connectionDetails);
            assertThat(context.getBean(Authenticator.class)).isInstanceOf(PasswordAuthenticator.class);
            assertThat(context.getBean(ClusterEnvironment.class)).isNotNull();
            assertThat(context.getBean(Cluster.class)).isNotNull();
        }
    }

    @Test
    void autoConfigurationUsesTheApplicationObjectMapperForCouchbaseDocuments() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource("couchbase-jackson-test",
                            Map.of("spring.couchbase.connection-string", "couchbase://127.0.0.1",
                                    "spring.couchbase.username", "application", "spring.couchbase.password",
                                    "secret")));
            context.registerBean(ObjectMapper.class, () -> objectMapper);
            context.register(CouchbaseAutoConfiguration.class);
            context.refresh();

            ClusterEnvironment environment = context.getBean(ClusterEnvironment.class);
            byte[] serialized = environment.jsonSerializer().serialize(new CouchbaseDocument("Ada Lovelace"));
            String json = new String(serialized, StandardCharsets.UTF_8);
            CouchbaseDocument deserialized = environment.jsonSerializer().deserialize(CouchbaseDocument.class,
                    serialized);

            assertThat(json).contains("\"display_name\":\"Ada Lovelace\"");
            assertThat(deserialized.getDisplayName()).isEqualTo("Ada Lovelace");
        }
    }

    @Test
    void propertiesExposeDocumentedClientDefaults() {
        CouchbaseProperties properties = new CouchbaseProperties();

        assertThat(properties.getConnectionString()).isNull();
        assertThat(properties.getUsername()).isNull();
        assertThat(properties.getPassword()).isNull();
        assertThat(properties.getEnv().getIo().getMinEndpoints()).isEqualTo(1);
        assertThat(properties.getEnv().getIo().getMaxEndpoints()).isEqualTo(12);
        assertThat(properties.getEnv().getSsl().getEnabled()).isFalse();
        assertThat(properties.getEnv().getSsl().getBundle()).isNull();
        assertThat(properties.getEnv().getTimeouts().getConnect()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getEnv().getTimeouts().getDisconnect()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getEnv().getTimeouts().getKeyValueDurable()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getEnv().getTimeouts().getQuery()).isEqualTo(Duration.ofSeconds(75));
    }

    public static final class TestCouchbaseConnectionDetails implements CouchbaseConnectionDetails {

        @Override
        public String getConnectionString() {
            return "couchbase://127.0.0.1";
        }

        @Override
        public String getUsername() {
            return "service-connection";
        }

        @Override
        public String getPassword() {
            return "connection-secret";
        }

    }

    public static final class CouchbaseDocument {

        private String displayName;

        public CouchbaseDocument() {
        }

        CouchbaseDocument(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

    }

}
