/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_value_registry;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.value.registry.RuntimeInfoProvider;
import io.quarkus.value.registry.ValueRegistry;
import io.quarkus.value.registry.ValueRegistry.RuntimeInfo;
import io.quarkus.value.registry.ValueRegistry.RuntimeInfo.SimpleRuntimeInfo;
import io.quarkus.value.registry.ValueRegistry.RuntimeKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Quarkus_value_registryTest {
    @Test
    void runtimeKeyFactoriesExposeExpectedNamesAndTypes() {
        RuntimeKey<String> plainKey = RuntimeKey.key("plain");
        RuntimeKey<Long> typedKey = RuntimeKey.key("typed", Long.class);
        RuntimeKey<ExampleValue> classKey = RuntimeKey.key(ExampleValue.class);
        RuntimeKey<Integer> intKey = RuntimeKey.intKey("workers");
        RuntimeKey<Boolean> booleanKey = RuntimeKey.booleanKey("enabled");

        assertThat(plainKey.key()).isEqualTo("plain");
        assertThat(plainKey.type()).isEqualTo(String.class);
        assertThat(typedKey.key()).isEqualTo("typed");
        assertThat(typedKey.type()).isEqualTo(Long.class);
        assertThat(classKey.key()).isEqualTo(ExampleValue.class.getName());
        assertThat(classKey.type()).isEqualTo(ExampleValue.class);
        assertThat(intKey.key()).isEqualTo("workers");
        assertThat(intKey.type()).isEqualTo(Integer.class);
        assertThat(booleanKey.key()).isEqualTo("enabled");
        assertThat(booleanKey.type()).isEqualTo(Boolean.class);
    }

    @Test
    void runtimeKeyEqualityAndHashCodeUseTheLogicalKeyName() {
        RuntimeKey<String> stringKey = RuntimeKey.key("shared", String.class);
        RuntimeKey<Integer> integerKey = RuntimeKey.key("shared", Integer.class);
        RuntimeKey<String> differentKey = RuntimeKey.key("different", String.class);

        assertThat(stringKey)
                .isEqualTo(integerKey)
                .hasSameHashCodeAs(integerKey)
                .isNotEqualTo(differentKey)
                .isNotEqualTo("shared")
                .isNotEqualTo(null);
    }

    @Test
    void simpleRuntimeInfoReturnsTheConfiguredValue() {
        ExampleValue value = new ExampleValue("alpha", 7);
        RuntimeInfo<ExampleValue> info = SimpleRuntimeInfo.of(value);
        RuntimeInfo<String> nullInfo = SimpleRuntimeInfo.of(null);

        assertThat(info.get(new InMemoryValueRegistry())).isSameAs(value);
        assertThat(nullInfo.get(new InMemoryValueRegistry())).isNull();
    }

    @Test
    void valueRegistryStoresConstantsDefaultsAndRuntimeInfoByPublicKeys() {
        InMemoryValueRegistry registry = new InMemoryValueRegistry();
        RuntimeKey<String> host = RuntimeKey.key("server.host");
        RuntimeKey<Integer> port = RuntimeKey.intKey("server.port");
        RuntimeKey<String> endpoint = RuntimeKey.key("server.endpoint");
        RuntimeInfo<String> endpointInfo = currentRegistry -> "http://" + currentRegistry.get(host) + ":"
                + currentRegistry.get(port) + "/health";

        assertThat(registry.containsKey(host)).isFalse();
        assertThat(registry.get(host)).isNull();
        assertThat(registry.getOrDefault(host, "localhost")).isEqualTo("localhost");

        registry.register(host, "example.test");
        registry.register(port, 8080);
        registry.registerInfo(endpoint, endpointInfo);

        assertThat(registry.containsKey(host)).isTrue();
        assertThat(registry.containsKey(endpoint)).isTrue();
        assertThat(registry.get(host)).isEqualTo("example.test");
        assertThat(registry.getOrDefault(host, "localhost")).isEqualTo("example.test");
        assertThat(registry.get(port)).isEqualTo(8080);
        assertThat(registry.get(endpoint)).isEqualTo("http://example.test:8080/health");
        assertThat(registry.get(endpoint.key())).isSameAs(endpointInfo);

        registry.register(port, 8181);

        assertThat(registry.get(endpoint)).isEqualTo("http://example.test:8181/health");
    }

    @Test
    void classBasedRuntimeKeysCanAddressDerivedRuntimeObjects() {
        InMemoryValueRegistry registry = new InMemoryValueRegistry();
        RuntimeKey<String> host = RuntimeKey.key("client.host");
        RuntimeKey<Boolean> secure = RuntimeKey.booleanKey("client.secure");
        RuntimeKey<ServiceEndpoint> endpoint = RuntimeKey.key(ServiceEndpoint.class);

        registry.register(host, "api.example.test");
        registry.register(secure, true);
        registry.registerInfo(endpoint, currentRegistry -> {
            String scheme = currentRegistry.get(secure) ? "https" : "http";
            return new ServiceEndpoint(scheme, currentRegistry.get(host), "/status");
        });

        ServiceEndpoint actual = registry.get(endpoint);

        assertThat(actual).isEqualTo(new ServiceEndpoint("https", "api.example.test", "/status"));
        assertThat(actual.uri()).isEqualTo("https://api.example.test/status");
    }

    @Test
    void independentlyCreatedRuntimeKeysWithTheSameNameShareRegisteredValues() {
        InMemoryValueRegistry registry = new InMemoryValueRegistry();
        RuntimeKey<String> registrationKey = RuntimeKey.key("feature.mode");
        RuntimeKey<String> lookupKey = RuntimeKey.key("feature.mode", String.class);
        RuntimeKey<String> derivedKey = RuntimeKey.key("feature.summary");

        registry.register(registrationKey, "active");
        registry.registerInfo(derivedKey, currentRegistry -> "mode=" + currentRegistry.get(lookupKey));

        assertThat(registry.containsKey(lookupKey)).isTrue();
        assertThat(registry.get(lookupKey)).isEqualTo("active");
        assertThat(registry.get(derivedKey)).isEqualTo("mode=active");
    }

    @Test
    void runtimeInfoProviderCanRegisterValuesFromARuntimeSource() {
        InMemoryValueRegistry registry = new InMemoryValueRegistry();
        MapRuntimeSource source = new MapRuntimeSource();
        ServerRuntimeInfoProvider provider = new ServerRuntimeInfoProvider();
        source.put(ServerRuntimeInfoProvider.HOST, "127.0.0.1");
        source.put(ServerRuntimeInfoProvider.PORT, 9000);

        provider.register(registry, source);

        assertThat(registry.get(ServerRuntimeInfoProvider.HOST)).isEqualTo("127.0.0.1");
        assertThat(registry.get(ServerRuntimeInfoProvider.BASE_URI)).isEqualTo("http://127.0.0.1:9000");

        source.put(ServerRuntimeInfoProvider.PORT, 9443);

        assertThat(registry.get(ServerRuntimeInfoProvider.BASE_URI)).isEqualTo("http://127.0.0.1:9443");
    }

    private record ExampleValue(String name, int priority) {
    }

    private record ServiceEndpoint(String scheme, String host, String path) {
        private String uri() {
            return scheme + "://" + host + path;
        }
    }

    private static final class InMemoryValueRegistry implements ValueRegistry {
        private final Map<String, RuntimeInfo<?>> values = new HashMap<>();

        @Override
        public <T> void register(RuntimeKey<T> key, T value) {
            registerInfo(key, SimpleRuntimeInfo.of(value));
        }

        @Override
        public <T> void registerInfo(RuntimeKey<T> key, RuntimeInfo<T> info) {
            values.put(key.key(), info);
        }

        @Override
        public <T> T get(RuntimeKey<T> key) {
            RuntimeInfo<?> info = values.get(key.key());
            if (info == null) {
                return null;
            }
            Object value = info.get(this);
            return key.type().cast(value);
        }

        @Override
        public <T> T getOrDefault(RuntimeKey<T> key, T defaultValue) {
            if (!containsKey(key)) {
                return defaultValue;
            }
            return get(key);
        }

        @Override
        public <T> boolean containsKey(RuntimeKey<T> key) {
            return values.containsKey(key.key());
        }

        @Override
        public RuntimeInfo<?> get(String key) {
            return values.get(key);
        }
    }

    private static final class MapRuntimeSource implements RuntimeInfoProvider.RuntimeSource {
        private final Map<RuntimeKey<?>, Object> values = new HashMap<>();

        private <T> void put(RuntimeKey<T> key, T value) {
            values.put(key, value);
        }

        @Override
        public <T> T get(RuntimeKey<T> key) {
            Object value = values.get(key);
            return key.type().cast(value);
        }
    }

    private static final class ServerRuntimeInfoProvider implements RuntimeInfoProvider {
        private static final RuntimeKey<String> HOST = RuntimeKey.key("host");
        private static final RuntimeKey<Integer> PORT = RuntimeKey.intKey("port");
        private static final RuntimeKey<String> BASE_URI = RuntimeKey.key("base-uri");

        @Override
        public void register(ValueRegistry registry, RuntimeSource source) {
            registry.register(HOST, source.get(HOST));
            registry.registerInfo(BASE_URI, currentRegistry -> "http://" + currentRegistry.get(HOST) + ":" + source.get(PORT));
        }
    }
}
