/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_common.helidon_common_config;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigBuilderSupport;
import io.helidon.common.config.ConfigException;
import io.helidon.common.config.ConfigValue;
import io.helidon.common.config.ConfiguredProvider;
import io.helidon.common.config.GlobalConfig;
import io.helidon.common.config.NamedService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HelidonCommonConfigTest {
    @Test
    void emptyConfigExposesAbsentNodeSemantics() {
        Config root = Config.empty();
        Config child = root.get("server.port");

        assertThat(root.exists()).isFalse();
        assertThat(root.isLeaf()).isFalse();
        assertThat(root.isObject()).isFalse();
        assertThat(root.isList()).isFalse();
        assertThat(root.hasValue()).isFalse();
        assertThat(root.traverse()).isEmpty();
        assertThat(root.root()).isSameAs(root);
        assertThat(root.detach()).isSameAs(root);

        assertThat(child.exists()).isFalse();
        assertThat(child.root()).isSameAs(root);
        assertThat(child.detach()).isSameAs(root);
        assertThat(child.key().toString()).isEqualTo("server.port");
        assertThat(child.name()).isEqualTo("port");
        assertThat(child.key().parent().toString()).isEqualTo("server");
    }

    @Test
    void emptyConfigCreatesEquivalentNodesFromStringAndKey() {
        Config root = Config.empty();
        Config first = root.get("app.features.audit");
        Config second = root.get(first.key());

        assertThat(second.key()).isEqualTo(first.key());
        assertThat(second.key().hashCode()).isEqualTo(first.key().hashCode());
        assertThat(second.key().compareTo(first.key())).isZero();
        assertThat(first.key().parent().parent().toString()).isEqualTo("app");
        assertThat(first.key().parent().parent().parent().isRoot()).isTrue();
        assertThatThrownBy(() -> first.key().parent().parent().parent().parent())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parent of a root node");
    }

    @Test
    void configValueForMissingNodeIsEmptyAndSuppliesFallbacks() {
        ConfigValue<String> value = Config.empty().get("missing.value").asString();
        Supplier<String> throwingSupplier = value.supplier();
        Supplier<String> fallbackSupplier = value.supplier("fallback");
        Supplier<Optional<String>> optionalSupplier = value.optionalSupplier();

        assertThat(value.key().toString()).isEqualTo("missing.value");
        assertThat(value.name()).isEqualTo("value");
        assertThat(value.asOptional()).isEmpty();
        assertThat(value.isPresent()).isFalse();
        assertThat(value.isEmpty()).isTrue();
        assertThat(value.orElse("default")).isEqualTo("default");
        assertThat(value.orElseGet(() -> "computed")).isEqualTo("computed");
        assertThat(value.or(() -> Optional.of("alternative"))).contains("alternative");
        assertThat(value.map(String::toUpperCase)).isEmpty();
        assertThat(value.filter(text -> true)).isEmpty();
        assertThat(value.stream()).isEmpty();
        assertThat(fallbackSupplier.get()).isEqualTo("fallback");
        assertThat(optionalSupplier.get()).isEmpty();
        assertThatThrownBy(value::get)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("Config node value is empty");
        assertThatThrownBy(throwingSupplier::get)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("Config node value is empty");
    }

    @Test
    void typedAccessorsOnMissingConfigReturnEmptyValues() {
        Config config = Config.empty().get("typed");

        assertThat(config.asBoolean().asOptional()).isEmpty();
        assertThat(config.asInt().asOptional()).isEmpty();
        assertThat(config.asLong().asOptional()).isEmpty();
        assertThat(config.asDouble().asOptional()).isEmpty();
        assertThat(config.asNode().asOptional()).isEmpty();
        assertThat(config.asList(String.class).asOptional()).isEmpty();
        assertThat(config.map(node -> node.name()).asOptional()).isEmpty();
        assertThat(config.mapList(node -> node.name()).asOptional()).isEmpty();
        assertThat(config.asNodeList().asOptional()).isEmpty();
        assertThat(config.asMap().asOptional()).isEmpty();
    }

    @Test
    void createFallsBackToEmptyConfigWhenNoProviderIsRegistered() {
        Config config = Config.create();

        assertThat(config.exists()).isFalse();
        assertThat(config.key().isRoot()).isTrue();
        assertThat(config.get("any.path").asString().asOptional()).isEmpty();
    }

    @Test
    void globalConfigStoresExplicitConfigAndHonorsOverwriteFlag() {
        Config first = Config.empty().get("first");
        Config second = Config.empty().get("second");
        AtomicInteger supplierCalls = new AtomicInteger();

        GlobalConfig.config(first, false);

        assertThat(GlobalConfig.configured()).isTrue();
        assertThat(GlobalConfig.config()).isSameAs(first);
        assertThat(GlobalConfig.config(() -> {
            supplierCalls.incrementAndGet();
            return second;
        })).isSameAs(first);
        assertThat(GlobalConfig.config(() -> {
            supplierCalls.incrementAndGet();
            return second;
        }, true)).isSameAs(second);
        assertThat(GlobalConfig.config()).isSameAs(second);
        assertThat(supplierCalls).hasValue(1);
    }

    @Test
    void configBuilderSupportReturnsEmptyResultsWhenNoProvidersAreDiscovered() {
        TestService defaultService = new TestService("default", "default-type");

        List<TestService> services = ConfigBuilderSupport.discoverServices(
                Config.empty(),
                "services",
                TestProvider.class,
                TestService.class,
                false,
                List.of());
        Optional<TestService> service = ConfigBuilderSupport.discoverService(
                Config.empty(),
                "service",
                TestProvider.class,
                TestService.class,
                false,
                Optional.empty());
        Optional<TestService> configuredDefault = ConfigBuilderSupport.discoverService(
                Config.empty(),
                "service",
                TestProvider.class,
                TestService.class,
                true,
                Optional.of(defaultService));

        assertThat(services).isEmpty();
        assertThat(service).isEmpty();
        assertThat(configuredDefault).isEmpty();
    }

    public record TestService(String name, String type) implements NamedService {
    }

    public static final class TestProvider implements ConfiguredProvider<TestService> {
        @Override
        public String configKey() {
            return "test";
        }

        @Override
        public TestService create(Config config, String name) {
            return new TestService(name, configKey());
        }
    }
}
