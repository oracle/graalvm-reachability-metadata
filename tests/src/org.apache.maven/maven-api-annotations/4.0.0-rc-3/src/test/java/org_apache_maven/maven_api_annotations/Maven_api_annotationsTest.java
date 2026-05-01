/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_annotations;

import org.apache.maven.api.annotations.Config;
import org.apache.maven.api.annotations.Consumer;
import org.apache.maven.api.annotations.Experimental;
import org.apache.maven.api.annotations.Generated;
import org.apache.maven.api.annotations.Immutable;
import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.annotations.NotThreadSafe;
import org.apache.maven.api.annotations.Nullable;
import org.apache.maven.api.annotations.Provider;
import org.apache.maven.api.annotations.ThreadSafe;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class Maven_api_annotationsTest {
    @Test
    void configSourceEnumProvidesStablePublicConstantsAndLookup() {
        Config.Source[] sources = Config.Source.values();

        assertThat(sources)
                .containsExactly(Config.Source.SYSTEM_PROPERTIES, Config.Source.USER_PROPERTIES, Config.Source.MODEL);
        assertThat(Config.Source.valueOf("SYSTEM_PROPERTIES")).isSameAs(Config.Source.SYSTEM_PROPERTIES);
        assertThat(Config.Source.valueOf("USER_PROPERTIES")).isSameAs(Config.Source.USER_PROPERTIES);
        assertThat(Config.Source.valueOf("MODEL")).isSameAs(Config.Source.MODEL);
        assertThatIllegalArgumentException().isThrownBy(() -> Config.Source.valueOf("ENVIRONMENT"));
    }

    @Test
    void configSourceValuesSupportSwitchBasedConfigurationRouting() {
        List<String> routedSources = new ArrayList<>();
        for (Config.Source source : Config.Source.values()) {
            routedSources.add(route(source));
        }

        assertThat(routedSources).containsExactly("jvm-system-property", "maven-user-property", "project-model");
    }

    @Test
    void configAnnotationContractCanDescribeTypedOptions() {
        Config readOnlyFlag = config(Config.Source.SYSTEM_PROPERTIES, "java.lang.Boolean", "true", true);
        Config modelCoordinate = config(Config.Source.MODEL, "java.lang.String", "", false);

        assertThat(format(readOnlyFlag)).isEqualTo("SYSTEM_PROPERTIES:java.lang.Boolean=true (read-only)");
        assertThat(format(modelCoordinate)).isEqualTo("MODEL:java.lang.String=<none> (writable)");
    }

    @Test
    void annotationsCanBeAppliedToNullabilityContracts() {
        NullabilityContract contract = new NullabilityContract("maven-api");

        assertThat(contract.normalizeRequired("ANNOTATIONS")).isEqualTo("annotations");
        assertThat(contract.describeOptional(null)).isEmpty();
        assertThat(contract.describeOptional("metadata")).contains("maven-api:metadata");
    }

    @Test
    void typeRoleAnnotationsCanBeUsedOnCollaboratingApiTypes() {
        ImmutableProvider provider = new ImmutableProvider("central");
        MutableConsumer consumer = new MutableConsumer();

        consumer.accept(provider);
        consumer.accept(new ExperimentalProvider("local"));

        assertThat(provider.id()).isEqualTo("CENTRAL");
        assertThat(consumer.seenProviderIds()).containsExactly("CENTRAL", "LOCAL");
    }

    @Test
    void generatedAndExperimentalAnnotationsCanMarkPublicApiShapes() {
        GeneratedEndpoint endpoint = new GeneratedEndpoint("compile-time-generated");

        assertThat(endpoint.displayName()).isEqualTo("COMPILE-TIME-GENERATED");
        assertThat(endpoint.description("api")).isEqualTo("COMPILE-TIME-GENERATED:api");
    }

    private static String route(Config.Source source) {
        return switch (source) {
            case SYSTEM_PROPERTIES -> "jvm-system-property";
            case USER_PROPERTIES -> "maven-user-property";
            case MODEL -> "project-model";
        };
    }

    private static String format(Config config) {
        String defaultValue = config.defaultValue().isEmpty() ? "<none>" : config.defaultValue();
        String mutability = config.readOnly() ? "read-only" : "writable";
        return config.source() + ":" + config.type() + "=" + defaultValue + " (" + mutability + ")";
    }

    private static Config config(
            Config.Source source, String type, String defaultValue, boolean readOnly) {
        return new Config() {
            @Override
            public Config.Source source() {
                return source;
            }

            @Override
            public String type() {
                return type;
            }

            @Override
            public String defaultValue() {
                return defaultValue;
            }

            @Override
            public boolean readOnly() {
                return readOnly;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Config.class;
            }
        };
    }

    @ThreadSafe
    @Provider
    @Immutable
    private static class ImmutableProvider {
        @Config(source = Config.Source.MODEL, defaultValue = "central", readOnly = true)
        private final String providerId;

        private ImmutableProvider(@Nonnull String providerId) {
            this.providerId = providerId;
        }

        @Nonnull
        private String id() {
            return providerId.toUpperCase(Locale.ROOT);
        }
    }

    @NotThreadSafe
    @Consumer
    private static final class MutableConsumer {
        private final List<String> providerIds = new ArrayList<>();

        private void accept(@Nonnull ImmutableProvider provider) {
            providerIds.add(provider.id());
        }

        private List<String> seenProviderIds() {
            return List.copyOf(providerIds);
        }
    }

    @Experimental
    @Provider
    private static final class ExperimentalProvider extends ImmutableProvider {
        private ExperimentalProvider(@Nonnull String providerId) {
            super(providerId);
        }
    }

    private static final class NullabilityContract {
        @Config(defaultValue = "maven-api", type = "java.lang.String")
        private final String prefix;

        private NullabilityContract(@Nonnull String prefix) {
            this.prefix = prefix;
        }

        @Nonnull
        private String normalizeRequired(@Nonnull String value) {
            return value.toLowerCase(Locale.ROOT);
        }

        @Nonnull
        private Optional<String> describeOptional(@Nullable String value) {
            return Optional.ofNullable(value).map(nonNullValue -> prefix + ":" + nonNullValue);
        }
    }

    @Generated
    @Experimental
    private static final class GeneratedEndpoint {
        @Nullable
        private final String name;

        private GeneratedEndpoint(@Nullable String name) {
            this.name = name;
        }

        @Nonnull
        private String displayName() {
            return Optional.ofNullable(name).orElse("unnamed").toUpperCase(Locale.ROOT);
        }

        @Experimental
        private String description(@Nonnull String suffix) {
            return displayName() + ":" + suffix;
        }
    }
}
