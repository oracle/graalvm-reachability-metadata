/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_microprofile_config.microprofile_config_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;

@Timeout(10)
public class Microprofile_config_apiTest {
    @Test
    void configSourceDefaultMethodsExposePropertiesAndOrdinalFallbacks() {
        MapBackedConfigSource source = new MapBackedConfigSource("test-source", Map.of(
                "alpha", "one",
                "beta", "two",
                ConfigSource.CONFIG_ORDINAL, "275"));

        assertThat(source.getProperties())
                .containsEntry("alpha", "one")
                .containsEntry("beta", "two")
                .containsEntry(ConfigSource.CONFIG_ORDINAL, "275");
        assertThat(source.getOrdinal()).isEqualTo(275);

        MapBackedConfigSource sourceWithoutOrdinal = new MapBackedConfigSource(
                "default-ordinal", Map.of("alpha", "one"));
        MapBackedConfigSource sourceWithInvalidOrdinal = new MapBackedConfigSource("invalid-ordinal", Map.of(
                ConfigSource.CONFIG_ORDINAL, "not-an-integer"));

        assertThat(sourceWithoutOrdinal.getOrdinal()).isEqualTo(ConfigSource.DEFAULT_ORDINAL);
        assertThat(sourceWithInvalidOrdinal.getOrdinal()).isEqualTo(ConfigSource.DEFAULT_ORDINAL);
    }

    @Test
    void configDefaultListMethodsUseArrayLookups() {
        InMemoryConfig config = InMemoryConfig.builder()
                .withSources(new MapBackedConfigSource("lists", Map.of(
                        "colors", "red,green,blue",
                        "numbers", "1,2,3")))
                .build();

        assertThat(config.getValues("colors", String.class)).containsExactly("red", "green", "blue");
        assertThat(config.getOptionalValues("numbers", Integer.class))
                .hasValueSatisfying(values -> assertThat(values).containsExactly(1, 2, 3));
        assertThat(config.getOptionalValues("missing", String.class)).isEmpty();
    }

    @Test
    void configReadsHighestOrdinalSourceAndConvertsValues() {
        MapBackedConfigSource lowPriority = new MapBackedConfigSource("low", Map.of(
                ConfigSource.CONFIG_ORDINAL, "100",
                "answer", "low",
                "only.low", "present"));
        MapBackedConfigSource highPriority = new MapBackedConfigSource("high", Map.of(
                ConfigSource.CONFIG_ORDINAL, "500",
                "answer", "high",
                "enabled", "true",
                "message", "microprofile"));

        InMemoryConfig config = InMemoryConfig.builder()
                .withSources(lowPriority, highPriority)
                .withConverter(UpperCaseValue.class, 100, value -> new UpperCaseValue(value.toUpperCase()))
                .build();

        assertThat(config.getValue("answer", String.class)).isEqualTo("high");
        assertThat(config.getValue("enabled", Boolean.class)).isTrue();
        assertThat(config.getValue("message", UpperCaseValue.class).value()).isEqualTo("MICROPROFILE");
        assertThat(config.getConverter(UpperCaseValue.class))
                .hasValueSatisfying(converter -> assertThat(converter.convert("api").value()).isEqualTo("API"));
        assertThat(config.getOptionalValue("missing", String.class)).isEmpty();
        assertThatThrownBy(() -> config.getValue("missing", String.class)).isInstanceOf(NoSuchElementException.class);
        assertThat(config.getPropertyNames()).containsExactlyInAnyOrder(
                ConfigSource.CONFIG_ORDINAL, "answer", "only.low", "enabled", "message");
        assertThat(config.getConfigSources()).containsExactly(highPriority, lowPriority);
        assertThat(config.unwrap(InMemoryConfig.class)).isSameAs(config);
    }

    @Test
    void configValueDescribesResolvedSourceMetadata() {
        MapBackedConfigSource source = new MapBackedConfigSource("metadata", Map.of(
                ConfigSource.CONFIG_ORDINAL, "350",
                "host", "localhost"));
        InMemoryConfig config = InMemoryConfig.builder().withSources(source).build();

        ConfigValue value = config.getConfigValue("host");

        assertThat(value.getName()).isEqualTo("host");
        assertThat(value.getRawValue()).isEqualTo("localhost");
        assertThat(value.getValue()).isEqualTo("localhost");
        assertThat(value.getSourceName()).isEqualTo("metadata");
        assertThat(value.getSourceOrdinal()).isEqualTo(350);

        ConfigValue missing = config.getConfigValue("missing");
        assertThat(missing.getName()).isEqualTo("missing");
        assertThat(missing.getRawValue()).isNull();
        assertThat(missing.getSourceName()).isNull();
    }

    @Test
    void configSourceProviderReturnsConfiguredSources() {
        MapBackedConfigSource source = new MapBackedConfigSource("provided", Map.of("key", "value"));
        StaticConfigSourceProvider provider = new StaticConfigSourceProvider(List.of(source));

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Iterable<ConfigSource> configSources = provider.getConfigSources(classLoader);

        assertThat(configSources).containsExactly(source);
    }

    @Test
    @ResourceLock("microprofile-config-provider-resolver")
    void configProviderDelegatesToConfiguredResolverAndBuilder() {
        SimpleConfigProviderResolver resolver = new SimpleConfigProviderResolver();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader testClassLoader = originalClassLoader;
        ConfigProviderResolver.setInstance(resolver);
        try {
            Config config = resolver.getBuilder()
                    .forClassLoader(testClassLoader)
                    .addDefaultSources()
                    .addDiscoveredSources()
                    .addDiscoveredConverters()
                    .withSources(new MapBackedConfigSource("provider", Map.of("name", "configured")))
                    .build();
            resolver.registerConfig(config, testClassLoader);

            assertThat(ConfigProvider.getConfig()).isSameAs(config);
            assertThat(ConfigProvider.getConfig(testClassLoader)).isSameAs(config);

            resolver.releaseConfig(config);
            assertThatThrownBy(() -> ConfigProvider.getConfig(testClassLoader))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            ConfigProviderResolver.setInstance(null);
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    @ResourceLock("microprofile-config-provider-resolver")
    void configProviderResolvesConfigsByThreadContextAndExplicitClassLoaders() {
        SimpleConfigProviderResolver resolver = new SimpleConfigProviderResolver();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader contextClassLoader = new IsolatedClassLoader(originalClassLoader);
        ClassLoader explicitClassLoader = new IsolatedClassLoader(originalClassLoader);
        ConfigProviderResolver.setInstance(resolver);
        try {
            Config contextConfig = InMemoryConfig.builder()
                    .withSources(new MapBackedConfigSource("context", Map.of("scope", "thread-context")))
                    .build();
            Config explicitConfig = InMemoryConfig.builder()
                    .withSources(new MapBackedConfigSource("explicit", Map.of("scope", "explicit")))
                    .build();
            resolver.registerConfig(contextConfig, contextClassLoader);
            resolver.registerConfig(explicitConfig, explicitClassLoader);
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            assertThat(ConfigProvider.getConfig()).isSameAs(contextConfig);
            assertThat(ConfigProvider.getConfig().getValue("scope", String.class)).isEqualTo("thread-context");
            assertThat(ConfigProvider.getConfig(explicitClassLoader)).isSameAs(explicitConfig);
            assertThat(ConfigProvider.getConfig(explicitClassLoader).getValue("scope", String.class))
                    .isEqualTo("explicit");
        } finally {
            ConfigProviderResolver.setInstance(null);
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void configPropertiesLiteralAndConstantsExposeSpecifiedSentinels() {
        ConfigProperties.Literal literal = ConfigProperties.Literal.of("server");

        assertThat(literal.prefix()).isEqualTo("server");
        assertThat(ConfigProperties.Literal.NO_PREFIX.prefix()).isEqualTo(ConfigProperties.UNCONFIGURED_PREFIX);
        assertThat(ConfigProperty.UNCONFIGURED_VALUE)
                .isEqualTo("org.eclipse.microprofile.config.configproperty.unconfigureddvalue");
        assertThat(Config.PROFILE).isEqualTo("mp.config.profile");
        assertThat(Config.PROPERTY_EXPRESSIONS_ENABLED).isEqualTo("mp.config.property.expressions.enabled");
    }

    @Test
    void configPropertiesLiteralSupportsQualifierAnnotationSemantics() {
        ConfigProperties.Literal server = ConfigProperties.Literal.of("server");
        ConfigProperties.Literal matchingServer = ConfigProperties.Literal.of("server");
        ConfigProperties.Literal client = ConfigProperties.Literal.of("client");

        assertThat(server).isInstanceOf(ConfigProperties.class);
        assertThat(server).isEqualTo(matchingServer);
        assertThat(server).hasSameHashCodeAs(matchingServer);
        assertThat(server).isNotEqualTo(client);
    }
}

final class MapBackedConfigSource implements ConfigSource {
    private final String name;
    private final Map<String, String> properties;

    MapBackedConfigSource(String name, Map<String, String> properties) {
        this.name = name;
        this.properties = new LinkedHashMap<>(properties);
    }

    @Override
    public Set<String> getPropertyNames() {
        return new LinkedHashSet<>(properties.keySet());
    }

    @Override
    public String getValue(String propertyName) {
        return properties.get(propertyName);
    }

    @Override
    public String getName() {
        return name;
    }
}

final class StaticConfigSourceProvider implements ConfigSourceProvider {
    private final List<ConfigSource> sources;

    StaticConfigSourceProvider(List<ConfigSource> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override
    public Iterable<ConfigSource> getConfigSources(ClassLoader forClassLoader) {
        return sources;
    }
}

final class IsolatedClassLoader extends ClassLoader {
    IsolatedClassLoader(ClassLoader parent) {
        super(parent);
    }
}

final class InMemoryConfig implements Config {
    private final List<ConfigSource> sources;
    private final Map<Class<?>, Converter<?>> converters;

    InMemoryConfig(List<ConfigSource> sources, Map<Class<?>, Converter<?>> converters) {
        this.sources = List.copyOf(sources);
        this.converters = Map.copyOf(converters);
    }

    static InMemoryConfigBuilder builder() {
        return new InMemoryConfigBuilder();
    }

    @Override
    public <T> T getValue(String propertyName, Class<T> propertyType) {
        return getOptionalValue(propertyName, propertyType)
                .orElseThrow(() -> new NoSuchElementException(propertyName));
    }

    @Override
    public ConfigValue getConfigValue(String propertyName) {
        for (ConfigSource source : sources) {
            String rawValue = source.getValue(propertyName);
            if (rawValue != null) {
                return new SimpleConfigValue(propertyName, rawValue, source.getName(), source.getOrdinal());
            }
        }
        return new SimpleConfigValue(propertyName, null, null, 0);
    }

    @Override
    public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
        String rawValue = getConfigValue(propertyName).getRawValue();
        if (rawValue == null) {
            return Optional.empty();
        }
        return Optional.of(convert(rawValue, propertyType));
    }

    @Override
    public Iterable<String> getPropertyNames() {
        Set<String> names = new LinkedHashSet<>();
        for (ConfigSource source : sources) {
            names.addAll(source.getPropertyNames());
        }
        return names;
    }

    @Override
    public Iterable<ConfigSource> getConfigSources() {
        return sources;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<Converter<T>> getConverter(Class<T> forType) {
        return Optional.ofNullable((Converter<T>) converters.get(forType));
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        if (InMemoryConfig.class.equals(type) || Config.class.equals(type)) {
            return type.cast(this);
        }
        throw new IllegalArgumentException("Unsupported unwrap type: " + type.getName());
    }

    @SuppressWarnings("unchecked")
    private <T> T convert(String rawValue, Class<T> propertyType) {
        Converter<T> converter = (Converter<T>) converters.get(propertyType);
        if (converter != null) {
            return converter.convert(rawValue);
        }
        if (String.class.equals(propertyType)) {
            return propertyType.cast(rawValue);
        }
        if (Integer.class.equals(propertyType)) {
            return propertyType.cast(Integer.valueOf(rawValue));
        }
        if (Boolean.class.equals(propertyType)) {
            return propertyType.cast(Boolean.valueOf(rawValue));
        }
        if (String[].class.equals(propertyType)) {
            return propertyType.cast(split(rawValue));
        }
        if (Integer[].class.equals(propertyType)) {
            String[] parts = split(rawValue);
            Integer[] values = new Integer[parts.length];
            for (int i = 0; i < parts.length; i++) {
                values[i] = Integer.valueOf(parts[i]);
            }
            return propertyType.cast(values);
        }
        throw new IllegalArgumentException("Unsupported conversion type: " + propertyType.getName());
    }

    private String[] split(String rawValue) {
        String[] parts = rawValue.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }
}

final class InMemoryConfigBuilder implements ConfigBuilder {
    private final List<ConfigSource> sources = new ArrayList<>();
    private final Map<Class<?>, Converter<?>> converters = new HashMap<>();

    @Override
    public InMemoryConfigBuilder addDefaultSources() {
        return this;
    }

    @Override
    public InMemoryConfigBuilder addDiscoveredSources() {
        return this;
    }

    @Override
    public InMemoryConfigBuilder addDiscoveredConverters() {
        return this;
    }

    @Override
    public InMemoryConfigBuilder forClassLoader(ClassLoader classLoader) {
        return this;
    }

    @Override
    public InMemoryConfigBuilder withSources(ConfigSource... configSources) {
        sources.addAll(List.of(configSources));
        return this;
    }

    @Override
    public InMemoryConfigBuilder withConverters(Converter<?>... converters) {
        return this;
    }

    @Override
    public <T> InMemoryConfigBuilder withConverter(Class<T> type, int priority, Converter<T> converter) {
        converters.put(type, converter);
        return this;
    }

    @Override
    public InMemoryConfig build() {
        List<ConfigSource> orderedSources = new ArrayList<>(sources);
        orderedSources.sort(Comparator.comparingInt(ConfigSource::getOrdinal).reversed());
        return new InMemoryConfig(orderedSources, converters);
    }
}

final class SimpleConfigProviderResolver extends ConfigProviderResolver {
    private final Map<ClassLoader, Config> configs = new HashMap<>();

    @Override
    public Config getConfig() {
        return getConfig(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public Config getConfig(ClassLoader classLoader) {
        Config config = configs.get(classLoader);
        if (config == null) {
            throw new IllegalStateException("No config registered for class loader");
        }
        return config;
    }

    @Override
    public ConfigBuilder getBuilder() {
        return InMemoryConfig.builder();
    }

    @Override
    public void registerConfig(Config config, ClassLoader classLoader) {
        configs.put(classLoader, config);
    }

    @Override
    public void releaseConfig(Config config) {
        configs.values().removeIf(config::equals);
    }
}

final class SimpleConfigValue implements ConfigValue {
    private final String name;
    private final String value;
    private final String sourceName;
    private final int sourceOrdinal;

    SimpleConfigValue(String name, String value, String sourceName, int sourceOrdinal) {
        this.name = name;
        this.value = value;
        this.sourceName = sourceName;
        this.sourceOrdinal = sourceOrdinal;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String getRawValue() {
        return value;
    }

    @Override
    public String getSourceName() {
        return sourceName;
    }

    @Override
    public int getSourceOrdinal() {
        return sourceOrdinal;
    }
}

record UpperCaseValue(String value) {
}
