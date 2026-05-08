/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeSet;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.FallbackConfigSourceInterceptor;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.RelocateConfigSourceInterceptor;
import io.smallrye.config.SecretKeysHandler;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

public class Smallrye_configTest {
    @Test
    void resolvesProfileSpecificValuesExpressionsSecretHandlersAndStructuredValues() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("app.host", "base.example.test");
        properties.put("%dev.app.host", "dev.example.test");
        properties.put("app.port", "8080");
        properties.put("app.enabled", "true");
        properties.put("app.timeout", "PT5S");
        properties.put("app.message", "Service ${app.host}:${app.port}");
        properties.put("app.secret", "${reverse::terces}");
        properties.put("app.list", "alpha,beta,gamma");
        properties.put("app.indexed[0]", "zero");
        properties.put("app.indexed[1]", "one");
        properties.put("app.map.primary", "10");
        properties.put("app.map.secondary", "20");

        SmallRyeConfig config = config(properties)
                .withProfile("dev")
                .withSecretKeysHandlers(new ReverseSecretKeysHandler())
                .withDefaultValue("app.defaulted", "fallback")
                .build();

        assertThat(config.getValue("app.host", String.class)).isEqualTo("dev.example.test");
        assertThat(config.getValue("app.port", Integer.class)).isEqualTo(8080);
        assertThat(config.getValue("app.enabled", Boolean.class)).isTrue();
        assertThat(config.getValue("app.timeout", Duration.class)).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.getValue("app.message", String.class)).isEqualTo("Service dev.example.test:8080");
        assertThat(config.getValue("app.secret", String.class)).isEqualTo("secret");
        assertThat(config.getValue("app.defaulted", String.class)).isEqualTo("fallback");

        assertThat(config.getValues("app.list", String.class)).containsExactly("alpha", "beta", "gamma");
        assertThat(config.getValue("app.indexed", String[].class)).containsExactly("zero", "one");
        assertThat(config.getValues("app.map", String.class, Integer.class))
                .containsEntry("primary", 10)
                .containsEntry("secondary", 20);

        ConfigValue host = config.getConfigValue("app.host");
        assertThat(host.getName()).isEqualTo("app.host");
        assertThat(host.getValue()).isEqualTo("dev.example.test");
        assertThat(host.getProfile()).isEqualTo("dev");
        assertThat(host.getConfigSourceName()).contains("in-memory-test-source");
        assertThat(config.getProfiles()).containsExactly("dev");
        assertThat(config.isPropertyPresent("app.host")).isTrue();
    }

    @Test
    void usesCustomConvertersImplicitConvertersAndOptionalLookups() {
        Map<String, String> properties = Map.of(
                "custom.upper", "smallrye",
                "custom.range", "1000-2000",
                "custom.uri", "https://smallrye.io/config/",
                "custom.empty", "");

        SmallRyeConfig config = config(properties)
                .withConverter(UppercaseValue.class, 200, new UppercaseValueConverter())
                .build();

        assertThat(config.getValue("custom.upper", UppercaseValue.class).value()).isEqualTo("SMALLRYE");
        assertThat(config.getValue("custom.range", PortRange.class))
                .extracting(PortRange::minimum, PortRange::maximum)
                .containsExactly(1000, 2000);
        assertThat(config.getValue("custom.uri", URI.class).getHost()).isEqualTo("smallrye.io");
        assertThat(config.getOptionalValue("custom.missing", String.class)).isEmpty();
        assertThat(config.getOptionalValue("custom.empty", String.class)).isEmpty();
        assertThat(config.getConverter(PortRange.class)).isPresent();

        assertThatThrownBy(() -> config.getValue("custom.missing", String.class))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("custom.missing");
    }

    @Test
    void bindsConfigMappingInterfacesWithDefaultsNestedGroupsAndMethodConverters() {
        try {
            Map<String, String> properties = new LinkedHashMap<>();
            properties.put("service.endpoint", "https://api.example.test/v1");
            properties.put("service.tags", "blue,green");
            properties.put("service.mode", "write");
            properties.put("service.security.roles[0]", "admin");
            properties.put("service.security.roles[1]", "operator");

            SmallRyeConfig config = config(properties)
                    .withMapping(ServiceMapping.class)
                    .build();

            ServiceMapping service = config.getConfigMapping(ServiceMapping.class);
            assertThat(service.endpoint()).isEqualTo(URI.create("https://api.example.test/v1"));
            assertThat(service.retries()).isEqualTo(3);
            assertThat(service.description()).isEmpty();
            assertThat(service.tags()).containsExactly("blue", "green");
            assertThat(service.mode()).isEqualTo(Mode.WRITE);
            assertThat(service.security().enabled()).isTrue();
            assertThat(service.security().roles()).containsExactly("admin", "operator");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void rejectsUnknownMappingPropertiesWhenValidationIsEnabled() {
        try {
            Map<String, String> properties = Map.of(
                    "service.endpoint", "https://api.example.test/v1",
                    "service.tags", "blue",
                    "service.mode", "read",
                    "service.security.roles[0]", "viewer",
                    "service.unmapped", "should-fail");
            SmallRyeConfigBuilder builder = config(properties).withMapping(ServiceMapping.class);

            builder.build();
        } catch (ConfigValidationException exception) {
            assertThat(exception).hasMessageContaining("service.unmapped");
            return;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        }
        throw new AssertionError("Expected unknown mapping validation to reject service.unmapped");
    }

    @Test
    void createsConfigSourcesFromFactoryUsingPreviouslyInitializedContext() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("factory.region", "eu-central");
        properties.put("factory.service", "payments");

        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(properties, "factory-bootstrap-source", 100))
                .withSources(new DerivedEndpointConfigSourceFactory())
                .addDefaultInterceptors()
                .build();

        assertThat(config.getValue("generated.endpoint", String.class))
                .isEqualTo("https://payments.eu-central.example.test");
        assertThat(config.getValue("generated.bootstrap-names", String.class))
                .contains("factory.region", "factory.service");
        assertThat(config.getConfigValue("generated.endpoint").getConfigSourceName())
                .contains("derived-factory-source");
    }

    @Test
    void registersAndReleasesConfigThroughMicroProfileProviderResolver() {
        ClassLoader classLoader = new IsolatedClassLoader(Smallrye_configTest.class.getClassLoader());
        SmallRyeConfig config = config(Map.of("provider.message", "registered-value")).build();
        ConfigProviderResolver resolver = ConfigProviderResolver.instance();

        resolver.registerConfig(config, classLoader);
        try {
            Config resolvedConfig = ConfigProvider.getConfig(classLoader);

            assertThat(resolvedConfig).isSameAs(config);
            assertThat(resolvedConfig.getValue("provider.message", String.class)).isEqualTo("registered-value");
        } finally {
            resolver.releaseConfig(config);
        }

        assertThatThrownBy(() -> ConfigProvider.getConfig(classLoader).getValue("provider.message", String.class))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("provider.message");
    }

    @Test
    void appliesRelocationAndFallbackInterceptorsWithoutChangingSourcePrecedence() {
        Map<String, String> lowPriority = Map.of(
                "legacy.timeout", "15",
                "legacy.name", "legacy-service");
        Map<String, String> highPriority = Map.of(
                "current.timeout", "30",
                "service.name", "current-service");

        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(lowPriority, "low-priority-source", 100))
                .withSources(new PropertiesConfigSource(highPriority, "high-priority-source", 300))
                .withInterceptors(new RelocateConfigSourceInterceptor(Map.of("timeout", "current.timeout")))
                .withInterceptors(new FallbackConfigSourceInterceptor(Map.of("service.name", "legacy.name")))
                .addDefaultInterceptors()
                .build();

        assertThat(config.getValue("timeout", Integer.class)).isEqualTo(30);
        assertThat(config.getValue("service.name", String.class)).isEqualTo("current-service");
        assertThat(config.getValue("legacy.timeout", Integer.class)).isEqualTo(15);
        assertThat(config.getConfigSource("PropertiesConfigSource[source=high-priority-source]")).isPresent();

        List<String> propertyNames = new ArrayList<>();
        config.getPropertyNames().forEach(propertyNames::add);
        assertThat(propertyNames).contains("current.timeout", "legacy.timeout", "service.name", "legacy.name");
    }

    private static SmallRyeConfigBuilder config(Map<String, String> properties) {
        return new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(properties, "in-memory-test-source", 100))
                .addDefaultInterceptors();
    }

    public static final class DerivedEndpointConfigSourceFactory implements ConfigSourceFactory {
        @Override
        public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
            String region = context.getValue("factory.region").getValue();
            String service = context.getValue("factory.service").getValue();

            TreeSet<String> bootstrapNames = new TreeSet<>();
            Iterator<String> names = context.iterateNames();
            while (names.hasNext()) {
                bootstrapNames.add(names.next());
            }

            Map<String, String> generated = new LinkedHashMap<>();
            generated.put("generated.endpoint", "https://" + service + "." + region + ".example.test");
            generated.put("generated.bootstrap-names", String.join(",", bootstrapNames));
            return List.of(new PropertiesConfigSource(generated, "derived-factory-source", 200));
        }
    }

    public static final class IsolatedClassLoader extends ClassLoader {
        private IsolatedClassLoader(ClassLoader parent) {
            super(parent);
        }
    }

    public static final class ReverseSecretKeysHandler implements SecretKeysHandler {
        @Override
        public String decode(String secret) {
            return new StringBuilder(secret).reverse().toString();
        }

        @Override
        public String getName() {
            return "reverse";
        }
    }

    public record UppercaseValue(String value) {
    }

    public static final class UppercaseValueConverter implements Converter<UppercaseValue> {
        @Override
        public UppercaseValue convert(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return new UppercaseValue(value.toUpperCase());
        }
    }

    public static final class PortRange {
        private final int minimum;
        private final int maximum;

        private PortRange(int minimum, int maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }

        public static PortRange of(String value) {
            String[] bounds = value.split("-", 2);
            return new PortRange(Integer.parseInt(bounds[0]), Integer.parseInt(bounds[1]));
        }

        public int minimum() {
            return minimum;
        }

        public int maximum() {
            return maximum;
        }
    }

    public enum Mode {
        READ,
        WRITE
    }

    public static final class ModeConverter implements Converter<Mode> {
        @Override
        public Mode convert(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return Mode.valueOf(value.toUpperCase());
        }
    }

    @ConfigMapping(prefix = "service")
    public interface ServiceMapping {
        URI endpoint();

        @WithDefault("3")
        int retries();

        Optional<String> description();

        List<String> tags();

        @WithConverter(ModeConverter.class)
        Mode mode();

        Security security();
    }

    public interface Security {
        @WithDefault("true")
        boolean enabled();

        @WithName("roles")
        List<String> roles();
    }
}
