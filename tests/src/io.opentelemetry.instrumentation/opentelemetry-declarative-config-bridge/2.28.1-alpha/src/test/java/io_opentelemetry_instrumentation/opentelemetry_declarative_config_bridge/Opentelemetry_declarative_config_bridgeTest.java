/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_declarative_config_bridge;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.instrumentation.config.bridge.ConfigPropertiesBackedConfigProvider;
import io.opentelemetry.instrumentation.config.bridge.ConfigPropertiesBackedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigPropertiesBridgeBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class Opentelemetry_declarative_config_bridgeTest {
    private static final ComponentLoader NOOP_COMPONENT_LOADER = new ComponentLoader() {
        @Override
        public <T> Iterable<T> load(Class<T> type) {
            return Collections.emptyList();
        }
    };

    @Test
    void builderExposesDeclarativeTreeAsFlatAutoconfigureProperties() {
        DeclarativeConfigProperties declarativeConfig = node(
                "jdbc", node(
                        "enabled", true,
                        "timeout", 2_500L,
                        "retry_count", 3,
                        "sampling_ratio", 0.25),
                "common", node(
                        "messaging", node(
                                "capture_headers/development",
                                List.of("message-id", "traceparent"))),
                "database", node(
                        "mapping", node(
                                "postgres", "postgresql",
                                "mysql", "mysql-db")));

        DeclarativeConfigPropertiesBridgeBuilder builder =
                new DeclarativeConfigPropertiesBridgeBuilder();
        ConfigProperties config = builder.build(declarativeConfig);

        assertThat(config.getBoolean("otel.instrumentation.jdbc.enabled")).isTrue();
        assertThat(config.getInt("otel.instrumentation.jdbc.retry-count")).isEqualTo(3);
        assertThat(config.getDouble("otel.instrumentation.jdbc.sampling-ratio")).isEqualTo(0.25);
        assertThat(config.getDuration("otel.instrumentation.jdbc.timeout"))
                .isEqualTo(Duration.ofMillis(2_500));
        assertThat(config.getList(
                "otel.instrumentation.common.messaging.capture-headers/development"))
                .containsExactly("message-id", "traceparent");
        assertThat(config.getMap("otel.instrumentation.database.mapping"))
                .containsOnly(Map.entry("postgres", "postgresql"), Map.entry("mysql", "mysql-db"));
        assertThat(config.getList("otel.instrumentation.missing.list")).isEmpty();
        assertThat(config.getMap("otel.instrumentation.missing.map")).isEmpty();
    }

    @Test
    void builderUnwrapsJavaInstrumentationRootAndAppliesMappingsAndOverrides() {
        DeclarativeConfigProperties instrumentationConfig = node(
                "java", node(
                        "custom", node(
                                "otel", node(
                                        "name", "mapped-from-declarative-config",
                                        "timeout", 123L))));

        ConfigProperties config = new DeclarativeConfigPropertiesBridgeBuilder()
                .addMapping("library.alias.", "custom.otel.")
                .addOverride("library.alias.name", "override-name")
                .addOverride("literal.list", List.of("first", "second"))
                .buildFromInstrumentationConfig(instrumentationConfig);

        assertThat(config.getString("library.alias.name")).isEqualTo("override-name");
        assertThat(config.getDuration("library.alias.timeout")).isEqualTo(Duration.ofMillis(123));
        assertThat(config.getList("literal.list")).containsExactly("first", "second");
        assertThat(new DeclarativeConfigPropertiesBridgeBuilder()
                .build((DeclarativeConfigProperties) null)
                .getString("otel.instrumentation.jdbc.enabled"))
                .isNull();
    }

    @Test
    void configPropertiesBackedDeclarativeConfigTranslatesFlatJavaAndGeneralKeys() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("otel.instrumentation.http.known-methods", List.of("GET", "POST"));
        values.put(
                "otel.instrumentation.http.client.capture-request-headers",
                List.of("x-request-id"));
        values.put("otel.instrumentation.sanitization.url.experimental.sensitive-query-parameters",
                List.of("password", "token"));
        values.put("otel.semconv-stability.opt-in", "http/dup");
        values.put("otel.jmx.enabled", true);
        values.put("otel.metric.export.interval", Duration.ofSeconds(30));
        ConfigProperties configProperties = new MapConfigProperties(values, NOOP_COMPONENT_LOADER);

        DeclarativeConfigProperties instrumentationConfig =
                ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
                        configProperties);
        DeclarativeConfigProperties javaConfig = instrumentationConfig.getStructured("java");
        DeclarativeConfigProperties generalConfig = instrumentationConfig.getStructured("general");

        assertThat(javaConfig.getStructured("common").getStructured("http")
                .getScalarList("known_methods", String.class))
                .containsExactly("GET", "POST");
        assertThat(generalConfig.getStructured("http").getStructured("client")
                .getScalarList("request_captured_headers", String.class))
                .containsExactly("x-request-id");
        assertThat(generalConfig.getStructured("sanitization").getStructured("url")
                .getScalarList("sensitive_query_parameters/development", String.class))
                .containsExactly("password", "token");
        assertThat(generalConfig.getStructured("semconv_stability").getString("opt_in"))
                .isEqualTo("http/dup");
        assertThat(javaConfig.getStructured("jmx").getBoolean("enabled")).isTrue();
        assertThat(javaConfig.getStructured("jmx").getStructured("discovery").getLong("delay"))
                .isEqualTo(30_000L);
        assertThat(instrumentationConfig.getComponentLoader()).isSameAs(NOOP_COMPONENT_LOADER);
    }

    @Test
    void configPropertiesBackedDeclarativeConfigTranslatesGenericJavaKeysAndDevelopmentSuffix() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("otel.instrumentation.spring-webmvc.controller.enabled", true);
        values.put("otel.instrumentation.jdbc.statement-cache.size", 128);
        values.put("otel.instrumentation.http.experimental-client-metrics.enabled", true);
        values.put("otel.instrumentation.kafka.experimental.consumer-headers",
                List.of("event-type", "tenant"));
        ConfigProperties configProperties = new MapConfigProperties(values, NOOP_COMPONENT_LOADER);

        DeclarativeConfigProperties javaConfig =
                ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
                        configProperties)
                        .getStructured("java");

        assertThat(javaConfig.getStructured("spring_webmvc").getStructured("controller")
                .getBoolean("enabled"))
                .isTrue();
        assertThat(javaConfig.getStructured("jdbc").getStructured("statement_cache")
                .getInt("size"))
                .isEqualTo(128);
        assertThat(javaConfig.getStructured("http")
                .getStructured("experimental_client_metrics/development")
                .getBoolean("enabled"))
                .isTrue();
        assertThat(javaConfig.getStructured("kafka")
                .getScalarList("consumer_headers/development", String.class))
                .containsExactly("event-type", "tenant");
    }

    @Test
    void configProviderExposesServicePeerMappingAsStructuredList() {
        Map<String, String> peerServiceMapping = new LinkedHashMap<>();
        peerServiceMapping.put("db.example.internal", "orders-database");
        peerServiceMapping.put("cache.example.internal", "redis-cache");
        Map<String, Object> values = Map.of(
                "otel.instrumentation.common.peer-service-mapping", peerServiceMapping,
                "otel.jmx.discovery.delay", Duration.ofSeconds(15));
        ConfigProvider provider = ConfigPropertiesBackedConfigProvider.create(
                new MapConfigProperties(values, NOOP_COMPONENT_LOADER));

        DeclarativeConfigProperties javaConfig = provider.getInstrumentationConfig()
                .getStructured("java");
        List<DeclarativeConfigProperties> mappings = javaConfig.getStructured("common")
                .getStructuredList("service_peer_mapping");

        assertThat(mappings).hasSize(2);
        assertThat(mappings.get(0).getString("peer")).isEqualTo("db.example.internal");
        assertThat(mappings.get(0).getString("service_name")).isEqualTo("orders-database");
        assertThat(mappings.get(1).getString("peer")).isEqualTo("cache.example.internal");
        assertThat(mappings.get(1).getString("service_name")).isEqualTo("redis-cache");
        assertThat(mappings.get(0).getComponentLoader()).isSameAs(NOOP_COMPONENT_LOADER);
        assertThat(javaConfig.getStructured("jmx").getStructured("discovery").getLong("delay"))
                .isEqualTo(15_000L);
    }

    private static DeclarativeConfigProperties node(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Entries must be key/value pairs");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put((String) entries[index], entries[index + 1]);
        }
        return new MapDeclarativeConfigProperties(values, NOOP_COMPONENT_LOADER);
    }

    private static final class MapDeclarativeConfigProperties
            implements DeclarativeConfigProperties {
        private final Map<String, Object> values;
        private final ComponentLoader componentLoader;

        private MapDeclarativeConfigProperties(
                Map<String, Object> values,
                ComponentLoader componentLoader) {
            this.values = values;
            this.componentLoader = componentLoader;
        }

        @Override
        public String getString(String name) {
            return getValue(name, String.class);
        }

        @Override
        public Boolean getBoolean(String name) {
            return getValue(name, Boolean.class);
        }

        @Override
        public Integer getInt(String name) {
            return getValue(name, Integer.class);
        }

        @Override
        public Long getLong(String name) {
            return getValue(name, Long.class);
        }

        @Override
        public Double getDouble(String name) {
            return getValue(name, Double.class);
        }

        @Override
        public <T> List<T> getScalarList(String name, Class<T> scalarType) {
            Object value = values.get(name);
            if (!(value instanceof List<?> entries)) {
                return null;
            }
            List<T> result = new ArrayList<>();
            for (Object entry : entries) {
                if (!scalarType.isInstance(entry)) {
                    return null;
                }
                result.add(scalarType.cast(entry));
            }
            return result;
        }

        @Override
        public DeclarativeConfigProperties getStructured(String name) {
            return getValue(name, DeclarativeConfigProperties.class);
        }

        @Override
        public List<DeclarativeConfigProperties> getStructuredList(String name) {
            Object value = values.get(name);
            if (!(value instanceof List<?> entries)) {
                return null;
            }
            List<DeclarativeConfigProperties> result = new ArrayList<>();
            for (Object entry : entries) {
                if (!(entry instanceof DeclarativeConfigProperties structuredEntry)) {
                    return null;
                }
                result.add(structuredEntry);
            }
            return result;
        }

        @Override
        public Set<String> getPropertyKeys() {
            return values.keySet();
        }

        @Override
        public ComponentLoader getComponentLoader() {
            return componentLoader;
        }

        private <T> T getValue(String name, Class<T> type) {
            Object value = values.get(name);
            if (value == null) {
                return null;
            }
            return type.cast(value);
        }
    }

    private static final class MapConfigProperties implements ConfigProperties {
        private final Map<String, Object> values;
        private final ComponentLoader componentLoader;

        private MapConfigProperties(Map<String, Object> values, ComponentLoader componentLoader) {
            this.values = values;
            this.componentLoader = componentLoader;
        }

        @Override
        public String getString(String name) {
            return getValue(name, String.class);
        }

        @Override
        public Boolean getBoolean(String name) {
            return getValue(name, Boolean.class);
        }

        @Override
        public Integer getInt(String name) {
            return getValue(name, Integer.class);
        }

        @Override
        public Long getLong(String name) {
            return getValue(name, Long.class);
        }

        @Override
        public Double getDouble(String name) {
            return getValue(name, Double.class);
        }

        @Override
        public Duration getDuration(String name) {
            return getValue(name, Duration.class);
        }

        @Override
        public List<String> getList(String name) {
            List<String> value = getValue(name, List.class);
            if (value == null) {
                return Collections.emptyList();
            }
            return value;
        }

        @Override
        public Map<String, String> getMap(String name) {
            Map<String, String> value = getValue(name, Map.class);
            if (value == null) {
                return Collections.emptyMap();
            }
            return value;
        }

        @Override
        public ComponentLoader getComponentLoader() {
            return componentLoader;
        }

        @SuppressWarnings("unchecked")
        private <T> T getValue(String name, Class<?> type) {
            Object value = values.get(name);
            if (value == null) {
                return null;
            }
            return (T) type.cast(value);
        }
    }
}
