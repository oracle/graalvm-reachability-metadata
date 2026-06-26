/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.resources.ContainerResource;
import io.opentelemetry.instrumentation.resources.ContainerResourceProvider;
import io.opentelemetry.instrumentation.resources.HostIdResourceProvider;
import io.opentelemetry.instrumentation.resources.HostResource;
import io.opentelemetry.instrumentation.resources.HostResourceProvider;
import io.opentelemetry.instrumentation.resources.JarServiceNameDetector;
import io.opentelemetry.instrumentation.resources.ManifestResourceProvider;
import io.opentelemetry.instrumentation.resources.OsResource;
import io.opentelemetry.instrumentation.resources.OsResourceProvider;
import io.opentelemetry.instrumentation.resources.ProcessResource;
import io.opentelemetry.instrumentation.resources.ProcessResourceProvider;
import io.opentelemetry.instrumentation.resources.ProcessRuntimeResource;
import io.opentelemetry.instrumentation.resources.ProcessRuntimeResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Opentelemetry_resourcesTest {
    private static final AttributeKey<String> CONTAINER_ID = AttributeKey.stringKey("container.id");
    private static final AttributeKey<String> HOST_ARCH = AttributeKey.stringKey("host.arch");
    private static final AttributeKey<String> HOST_ID = AttributeKey.stringKey("host.id");
    private static final AttributeKey<String> HOST_NAME = AttributeKey.stringKey("host.name");
    private static final AttributeKey<String> OS_DESCRIPTION = AttributeKey.stringKey("os.description");
    private static final AttributeKey<String> OS_TYPE = AttributeKey.stringKey("os.type");
    private static final AttributeKey<String> OS_VERSION = AttributeKey.stringKey("os.version");
    private static final AttributeKey<String> PROCESS_COMMAND_LINE = AttributeKey.stringKey("process.command_line");
    private static final AttributeKey<String> PROCESS_EXECUTABLE_PATH =
            AttributeKey.stringKey("process.executable.path");
    private static final AttributeKey<Long> PROCESS_PID = AttributeKey.longKey("process.pid");
    private static final AttributeKey<String> PROCESS_RUNTIME_DESCRIPTION =
            AttributeKey.stringKey("process.runtime.description");
    private static final AttributeKey<String> PROCESS_RUNTIME_NAME = AttributeKey.stringKey("process.runtime.name");
    private static final AttributeKey<String> PROCESS_RUNTIME_VERSION =
            AttributeKey.stringKey("process.runtime.version");
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> SERVICE_VERSION = AttributeKey.stringKey("service.version");
    private static final String UNKNOWN_JAVA_SERVICE_NAME = "unknown_service:java";

    @Test
    void resourceProviderServicesExposeAllPublicResourceProviders() {
        Set<Class<?>> providerClasses = StreamSupport.stream(
                        ServiceLoader.load(ResourceProvider.class).spliterator(), false)
                .map(Object::getClass)
                .filter(providerClass -> providerClass.getName()
                        .startsWith("io.opentelemetry.instrumentation.resources"))
                .collect(Collectors.toSet());

        assertThat(providerClasses)
                .contains(
                        ContainerResourceProvider.class,
                        HostResourceProvider.class,
                        JarServiceNameDetector.class,
                        ManifestResourceProvider.class,
                        OsResourceProvider.class,
                        ProcessResourceProvider.class,
                        ProcessRuntimeResourceProvider.class);
    }

    @Test
    void hostProviderReturnsHostArchitectureAndOptionalHostName() {
        Resource directResource = HostResource.get();
        Resource providerResource = new HostResourceProvider().createResource(EmptyConfigProperties.INSTANCE);

        assertThat(providerResource.getAttributes()).isEqualTo(directResource.getAttributes());
        assertThat(providerResource.getAttribute(HOST_ARCH)).isEqualTo(System.getProperty("os.arch"));
        String hostName = providerResource.getAttribute(HOST_NAME);
        if (hostName != null) {
            assertThat(hostName).isNotBlank();
        }
    }

    @Test
    void operatingSystemProviderMapsTheCurrentOperatingSystem() {
        Resource directResource = OsResource.get();
        Resource providerResource = new OsResourceProvider().createResource(EmptyConfigProperties.INSTANCE);

        assertThat(providerResource.getAttributes()).isEqualTo(directResource.getAttributes());
        assertThat(providerResource.getAttribute(OS_TYPE)).isEqualTo(expectedOsType(System.getProperty("os.name")));
        assertThat(providerResource.getAttribute(OS_VERSION)).isEqualTo(System.getProperty("os.version"));
        assertThat(providerResource.getAttribute(OS_DESCRIPTION))
                .isEqualTo(System.getProperty("os.name") + " " + System.getProperty("os.version"));
    }

    @Test
    void processRuntimeProviderDescribesTheCurrentJvm() {
        Resource directResource = ProcessRuntimeResource.get();
        Resource providerResource = new ProcessRuntimeResourceProvider().createResource(EmptyConfigProperties.INSTANCE);

        assertThat(providerResource.getAttributes()).isEqualTo(directResource.getAttributes());
        assertThat(providerResource.getAttribute(PROCESS_RUNTIME_NAME))
                .isEqualTo(System.getProperty("java.runtime.name"));
        assertThat(providerResource.getAttribute(PROCESS_RUNTIME_VERSION))
                .isEqualTo(System.getProperty("java.runtime.version"));
        assertThat(providerResource.getAttribute(PROCESS_RUNTIME_DESCRIPTION))
                .contains(System.getProperty("java.vm.vendor"))
                .contains(System.getProperty("java.vm.name"))
                .contains(System.getProperty("java.vm.version"));
    }

    @Test
    void processProviderReportsStableProcessAttributesWithoutDependingOnExactCommandLine() {
        Resource directResource = ProcessResource.get();
        Resource providerResource = new ProcessResourceProvider().createResource(EmptyConfigProperties.INSTANCE);

        assertThat(providerResource.getAttributes()).isEqualTo(directResource.getAttributes());
        Long pid = providerResource.getAttribute(PROCESS_PID);
        if (pid != null) {
            assertThat(pid).isPositive();
        }
        String executablePath = providerResource.getAttribute(PROCESS_EXECUTABLE_PATH);
        if (executablePath != null) {
            assertThat(executablePath).isNotBlank();
        }
        String commandLine = providerResource.getAttribute(PROCESS_COMMAND_LINE);
        if (commandLine != null) {
            assertThat(commandLine).isNotBlank();
        }
    }

    @Test
    void containerProviderAllowsMissingContainerIdentityOutsideContainerizedCgroups() {
        Resource directResource = ContainerResource.get();
        Resource providerResource = new ContainerResourceProvider().createResource(EmptyConfigProperties.INSTANCE);

        assertThat(providerResource.getAttributes()).isEqualTo(directResource.getAttributes());
        String containerId = providerResource.getAttribute(CONTAINER_ID);
        if (containerId == null) {
            assertThat(providerResource.getAttributes().isEmpty()).isTrue();
        } else {
            assertThat(containerId).isNotBlank();
        }
    }

    @Test
    void hostIdProviderIsSuppressedWhenHostIdAlreadyComesFromConfigurationOrEarlierResource() {
        HostIdResourceProvider provider = new HostIdResourceProvider();
        Resource currentResource = Resource.empty();

        assertThat(provider.shouldApply(EmptyConfigProperties.INSTANCE, currentResource)).isTrue();
        assertThat(provider.shouldApply(configWithResourceAttributes(Map.of("host.id", "configured")), currentResource))
                .isFalse();
        assertThat(provider.shouldApply(
                        EmptyConfigProperties.INSTANCE,
                        Resource.create(Attributes.of(HOST_ID, "already-detected"))))
                .isFalse();
        assertThat(provider.order()).isEqualTo(Integer.MAX_VALUE - 1);
    }

    @Test
    void jarServiceNameDetectorOnlyAppliesToTheDefaultUnknownJavaServiceName() {
        JarServiceNameDetector detector = new JarServiceNameDetector();
        Resource defaultServiceName = Resource.create(Attributes.of(SERVICE_NAME, UNKNOWN_JAVA_SERVICE_NAME));

        assertThat(detector.shouldApply(EmptyConfigProperties.INSTANCE, defaultServiceName)).isTrue();
        assertThat(detector.shouldApply(configWithServiceName("configured-service"), defaultServiceName)).isFalse();
        assertThat(detector.shouldApply(configWithResourceAttributes(Map.of("service.name", "configured-service")),
                        defaultServiceName))
                .isFalse();
        assertThat(detector.shouldApply(
                        EmptyConfigProperties.INSTANCE,
                        Resource.create(Attributes.of(SERVICE_NAME, "already-detected"))))
                .isFalse();
        assertThat(detector.order()).isEqualTo(1000);
    }

    @Test
    void jarAndManifestProvidersCreateServiceAttributesFromCurrentJar(@TempDir Path tempDir)
            throws IOException {
        Path jarPath = createJar(tempDir.resolve("checkout-service.jar"), "inventory-app", "test-build");
        ManifestResourceProvider manifestProvider = new ManifestResourceProvider();
        Resource defaultServiceName = Resource.create(Attributes.of(SERVICE_NAME, UNKNOWN_JAVA_SERVICE_NAME));
        String originalCommand = System.getProperty("sun.java.command");
        try {
            System.setProperty("sun.java.command", jarPath.toString());

            Resource jarNameResource = new JarServiceNameDetector().createResource(EmptyConfigProperties.INSTANCE);
            assertThat(jarNameResource.getAttribute(SERVICE_NAME)).isEqualTo("checkout-service");

            assertThat(manifestProvider.shouldApply(EmptyConfigProperties.INSTANCE, defaultServiceName)).isTrue();
            Resource manifestResource = manifestProvider.createResource(EmptyConfigProperties.INSTANCE);
            assertThat(manifestResource.getAttribute(SERVICE_NAME)).isEqualTo("inventory-app");
            assertThat(manifestResource.getAttribute(SERVICE_VERSION)).isEqualTo("test-build");
        } finally {
            restoreSystemProperty("sun.java.command", originalCommand);
        }
    }

    @Test
    void manifestProviderRespectsConfiguredResourceAttributesBeforeExtractingManifestData() {
        ManifestResourceProvider provider = new ManifestResourceProvider();
        Resource defaultServiceName = Resource.create(Attributes.of(SERVICE_NAME, UNKNOWN_JAVA_SERVICE_NAME));

        assertThat(provider.shouldApply(EmptyConfigProperties.INSTANCE, defaultServiceName)).isTrue();
        assertThat(provider.shouldApply(
                        configWithResourceAttributes(Map.of("service.name", "configured", "service.version", "1")),
                        defaultServiceName))
                .isFalse();
        assertThat(provider.order()).isEqualTo(300);
    }

    private static String expectedOsType(String osName) {
        String normalizedOsName = osName.toLowerCase(Locale.ROOT);
        if (normalizedOsName.startsWith("windows")) {
            return "windows";
        }
        if (normalizedOsName.startsWith("linux")) {
            return "linux";
        }
        if (normalizedOsName.startsWith("mac")) {
            return "darwin";
        }
        if (normalizedOsName.startsWith("freebsd")) {
            return "freebsd";
        }
        if (normalizedOsName.startsWith("netbsd")) {
            return "netbsd";
        }
        if (normalizedOsName.startsWith("openbsd")) {
            return "openbsd";
        }
        if (normalizedOsName.startsWith("dragonflybsd")) {
            return "dragonflybsd";
        }
        if (normalizedOsName.startsWith("hp-ux")) {
            return "hpux";
        }
        if (normalizedOsName.startsWith("aix")) {
            return "aix";
        }
        if (normalizedOsName.startsWith("solaris")) {
            return "solaris";
        }
        if (normalizedOsName.startsWith("z/os")) {
            return "zos";
        }
        return null;
    }

    private static ConfigProperties configWithResourceAttributes(Map<String, String> resourceAttributes) {
        Map<String, String> properties = new HashMap<>();
        resourceAttributes.forEach((key, value) -> properties.put("otel.resource.attributes." + key, value));
        return new TestConfigProperties(null, resourceAttributes, properties);
    }

    private static Path createJar(Path jarPath, String implementationTitle, String implementationVersion)
            throws IOException {
        Manifest manifest = new Manifest();
        java.util.jar.Attributes mainAttributes = manifest.getMainAttributes();
        mainAttributes.put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        if (implementationTitle != null) {
            mainAttributes.putValue("Implementation-Title", implementationTitle);
        }
        if (implementationVersion != null) {
            mainAttributes.putValue("Implementation-Version", implementationVersion);
        }
        try (OutputStream outputStream = Files.newOutputStream(jarPath);
                JarOutputStream ignored = new JarOutputStream(outputStream, manifest)) {
            return jarPath;
        }
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static ConfigProperties configWithServiceName(String serviceName) {
        return new TestConfigProperties(serviceName, Collections.emptyMap(), Collections.emptyMap());
    }

    private static class EmptyConfigProperties extends TestConfigProperties {
        private static final EmptyConfigProperties INSTANCE = new EmptyConfigProperties();

        EmptyConfigProperties() {
            super(null, Collections.emptyMap(), Collections.emptyMap());
        }
    }

    private static class TestConfigProperties implements ConfigProperties {
        private final String serviceName;
        private final Map<String, String> resourceAttributes;
        private final Map<String, String> properties;

        TestConfigProperties(
                String serviceName,
                Map<String, String> resourceAttributes,
                Map<String, String> properties) {
            this.serviceName = serviceName;
            this.resourceAttributes = resourceAttributes;
            this.properties = properties;
        }

        @Override
        public String getString(String name) {
            if ("otel.service.name".equals(name)) {
                return serviceName;
            }
            return properties.get(name);
        }

        @Override
        public Boolean getBoolean(String name) {
            return null;
        }

        @Override
        public Integer getInt(String name) {
            return null;
        }

        @Override
        public Long getLong(String name) {
            return null;
        }

        @Override
        public Double getDouble(String name) {
            return null;
        }

        @Override
        public Duration getDuration(String name) {
            return null;
        }

        @Override
        public List<String> getList(String name) {
            return Collections.emptyList();
        }

        @Override
        public Map<String, String> getMap(String name) {
            if ("otel.resource.attributes".equals(name)) {
                return resourceAttributes;
            }
            return Collections.emptyMap();
        }
    }
}
