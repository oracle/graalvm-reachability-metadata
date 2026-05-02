/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_annotations;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_api_annotationsTest {
    private static final String TEST_CLASS_BINARY_NAME =
            "org_apache_maven.maven_api_annotations.Maven_api_annotationsTest";

    @Test
    void configSourceEnumExposesAllSupportedConfigurationOrigins() {
        Config.Source[] sources = Config.Source.values();

        assertThat(sources).containsExactly(
                Config.Source.SYSTEM_PROPERTIES,
                Config.Source.USER_PROPERTIES,
                Config.Source.MODEL);
        assertThat(Config.Source.valueOf("SYSTEM_PROPERTIES")).isSameAs(Config.Source.SYSTEM_PROPERTIES);
        assertThat(Config.Source.valueOf("USER_PROPERTIES")).isSameAs(Config.Source.USER_PROPERTIES);
        assertThat(Config.Source.valueOf("MODEL")).isSameAs(Config.Source.MODEL);
        assertThatThrownBy(() -> Config.Source.valueOf("ENVIRONMENT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configSourceValuesReturnsADefensiveArrayCopy() {
        Config.Source[] firstRead = Config.Source.values();
        Config.Source[] secondRead = Config.Source.values();

        firstRead[0] = Config.Source.MODEL;

        assertThat(secondRead).containsExactly(
                Config.Source.SYSTEM_PROPERTIES,
                Config.Source.USER_PROPERTIES,
                Config.Source.MODEL);
        assertThat(Config.Source.values()).containsExactly(
                Config.Source.SYSTEM_PROPERTIES,
                Config.Source.USER_PROPERTIES,
                Config.Source.MODEL);
    }

    @Test
    void annotatedProviderConsumerAndConfigurationCodeExecutesNormally() {
        ProviderContract provider = new MetadataProvider("local", "workspace");
        ConsumerContract consumer = new MutableConsumer("consumer");
        ConfiguredComponent component = new ConfiguredComponent("apache-maven", "true", 3);

        assertThat(provider.describe("repository", "central")).isEqualTo("local:repository:workspace");
        assertThat(new MetadataProvider("remote", null).describe("repository", "central"))
                .isEqualTo("remote:repository:central");
        assertThat(provider.describe("mirror", null)).isEqualTo("local:mirror:workspace");
        assertThat(provider.optionalLabel(true)).isEqualTo("provider-local");
        assertThat(provider.optionalLabel(false)).isNull();
        assertThat(consumer.consume("metadata")).isEqualTo("consumer-metadata");
        assertThat(component.projectName()).isEqualTo("apache-maven");
        assertThat(component.batchMode()).isTrue();
        assertThat(component.retryCount()).isEqualTo(3);
        assertThat(component.defaultedScope()).isEqualTo("session");
    }

    @Test
    void experimentalContractCanBeImplementedWithAnnotatedMembers() {
        IncubatingContract contract = new IncubatingImplementation("preview");

        assertThat(contract.name()).isEqualTo("preview");
        assertThat(contract.render("api")).isEqualTo("preview-api");
    }

    @Test
    void immutableAnnotationDeclaresThreadSafeSemantics() {
        String immutableAnnotation = readLibraryClassFile(Immutable.class);

        assertThat(immutableAnnotation)
                .contains("RuntimeInvisibleAnnotations")
                .contains("org/apache/maven/api/annotations/ThreadSafe");
    }

    @Test
    void compiledFixturesContainMavenAnnotationDescriptors() {
        String providerContract = readClassFile(nestedClassName("ProviderContract"));
        String consumerContract = readClassFile(nestedClassName("ConsumerContract"));
        String metadataProvider = readClassFile(nestedClassName("MetadataProvider"));
        String mutableConsumer = readClassFile(nestedClassName("MutableConsumer"));
        String configuredComponent = readClassFile(nestedClassName("ConfiguredComponent"));
        String incubatingContract = readClassFile(nestedClassName("IncubatingContract"));

        assertThat(providerContract)
                .contains("RuntimeInvisibleAnnotations")
                .contains("org/apache/maven/api/annotations/Provider")
                .contains("org/apache/maven/api/annotations/Nonnull")
                .contains("org/apache/maven/api/annotations/Nullable");
        assertThat(consumerContract)
                .contains("org/apache/maven/api/annotations/Consumer")
                .contains("org/apache/maven/api/annotations/Nonnull");
        assertThat(metadataProvider)
                .contains("org/apache/maven/api/annotations/Generated")
                .contains("org/apache/maven/api/annotations/Immutable")
                .contains("org/apache/maven/api/annotations/ThreadSafe")
                .contains("org/apache/maven/api/annotations/Nonnull")
                .contains("org/apache/maven/api/annotations/Nullable");
        assertThat(mutableConsumer)
                .contains("org/apache/maven/api/annotations/NotThreadSafe")
                .contains("org/apache/maven/api/annotations/Nonnull");
        assertThat(configuredComponent)
                .contains("org/apache/maven/api/annotations/Config")
                .contains("SYSTEM_PROPERTIES")
                .contains("USER_PROPERTIES")
                .contains("MODEL")
                .contains("defaultValue")
                .contains("readOnly");
        assertThat(incubatingContract)
                .contains("org/apache/maven/api/annotations/Experimental")
                .contains("org/apache/maven/api/annotations/Nullable");
    }

    @Provider
    private interface ProviderContract {
        @Nonnull
        String id();

        @Nullable
        String label();

        @Nonnull
        default String describe(@Nonnull String key, @Nullable String fallback) {
            String suffix = label() == null ? fallback : label();
            return suffix == null ? id() + ":" + key : id() + ":" + key + ":" + suffix;
        }

        @Nullable
        default String optionalLabel(boolean enabled) {
            return enabled ? "provider-" + id() : null;
        }
    }

    @Consumer
    private interface ConsumerContract {
        @Nonnull
        String consume(@Nonnull String value);
    }

    @Generated
    @Immutable
    @ThreadSafe
    private static final class MetadataProvider implements ProviderContract {
        @Nonnull
        private final String id;

        @Nullable
        private final String label;

        private MetadataProvider(@Nonnull String id, @Nullable String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        @Nonnull
        public String id() {
            return id;
        }

        @Override
        @Nullable
        public String label() {
            return label;
        }
    }

    @NotThreadSafe
    private static final class MutableConsumer implements ConsumerContract {
        @Nonnull
        private String prefix;

        private MutableConsumer(@Nonnull String prefix) {
            this.prefix = prefix;
        }

        @Override
        @Nonnull
        public String consume(@Nonnull String value) {
            return prefix + "-" + value;
        }
    }

    private static final class ConfiguredComponent {
        @Config(source = Config.Source.USER_PROPERTIES, defaultValue = "sample-project")
        private final String projectName;

        @Config(source = Config.Source.SYSTEM_PROPERTIES, type = "boolean", defaultValue = "false", readOnly = true)
        private final boolean batchMode;

        @Config(source = Config.Source.MODEL, type = "java.lang.Integer", defaultValue = "1")
        private final int retryCount;

        @Config
        private final String defaultedScope;

        private ConfiguredComponent(String projectName, String batchMode, int retryCount) {
            this.projectName = projectName;
            this.batchMode = Boolean.parseBoolean(batchMode);
            this.retryCount = retryCount;
            this.defaultedScope = "session";
        }

        private String projectName() {
            return projectName;
        }

        private boolean batchMode() {
            return batchMode;
        }

        private int retryCount() {
            return retryCount;
        }

        private String defaultedScope() {
            return defaultedScope;
        }
    }

    @Experimental
    private interface IncubatingContract {
        @Nonnull
        String name();

        @Nonnull
        default String render(@Nullable String qualifier) {
            return qualifier == null ? name() : name() + "-" + qualifier;
        }
    }

    private static final class IncubatingImplementation implements IncubatingContract {
        @Nonnull
        private final String name;

        private IncubatingImplementation(@Nonnull String name) {
            this.name = name;
        }

        @Override
        @Nonnull
        public String name() {
            return name;
        }
    }

    private static String nestedClassName(String simpleName) {
        return TEST_CLASS_BINARY_NAME + "$" + simpleName;
    }

    private static String readClassFile(String binaryName) {
        Path classFilePath = Path.of("build", "classes", "java", "test")
                .resolve(binaryName.replace('.', '/') + ".class");

        assertThat(classFilePath).exists();
        try {
            return Files.readString(classFilePath, StandardCharsets.ISO_8859_1);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String readLibraryClassFile(Class<?> type) {
        String resourceName = type.getName().replace('.', '/') + ".class";

        try (InputStream inputStream = type.getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
