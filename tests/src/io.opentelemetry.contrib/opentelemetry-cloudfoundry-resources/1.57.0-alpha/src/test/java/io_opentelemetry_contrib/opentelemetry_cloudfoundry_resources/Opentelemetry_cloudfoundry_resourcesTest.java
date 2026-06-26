/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_contrib.opentelemetry_cloudfoundry_resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.contrib.cloudfoundry.resources.CloudFoundryResource;
import io.opentelemetry.contrib.cloudfoundry.resources.CloudFoundryResourceDetector;
import io.opentelemetry.contrib.cloudfoundry.resources.CloudFoundryResourceProvider;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Opentelemetry_cloudfoundry_resourcesTest {
    private static final String CLOUD_FOUNDRY_PROVIDER_NAME = CloudFoundryResourceProvider.class.getName();
    private static final AttributeKey<String> CLOUDFOUNDRY_APP_ID =
            AttributeKey.stringKey("cloudfoundry.app.id");
    private static final AttributeKey<String> CLOUDFOUNDRY_APP_INSTANCE_ID =
            AttributeKey.stringKey("cloudfoundry.app.instance.id");
    private static final AttributeKey<String> CLOUDFOUNDRY_APP_NAME =
            AttributeKey.stringKey("cloudfoundry.app.name");
    private static final AttributeKey<String> CLOUDFOUNDRY_ORG_ID =
            AttributeKey.stringKey("cloudfoundry.org.id");
    private static final AttributeKey<String> CLOUDFOUNDRY_ORG_NAME =
            AttributeKey.stringKey("cloudfoundry.org.name");
    private static final AttributeKey<String> CLOUDFOUNDRY_PROCESS_ID =
            AttributeKey.stringKey("cloudfoundry.process.id");
    private static final AttributeKey<String> CLOUDFOUNDRY_PROCESS_TYPE =
            AttributeKey.stringKey("cloudfoundry.process.type");
    private static final AttributeKey<String> CLOUDFOUNDRY_SPACE_ID =
            AttributeKey.stringKey("cloudfoundry.space.id");
    private static final AttributeKey<String> CLOUDFOUNDRY_SPACE_NAME =
            AttributeKey.stringKey("cloudfoundry.space.name");

    @Test
    void cloudFoundryResourceMapsVcapApplicationToSemanticAttributes() {
        Resource resource = CloudFoundryResource.get();

        assertCloudFoundryAttributes(resource);
        assertThat(resource.getAttributes().size()).isEqualTo(9);
        assertThat(resource.getSchemaUrl()).isEqualTo("https://opentelemetry.io/schemas/1.24.0");
    }

    @Test
    void resourceProviderReturnsCachedCloudFoundryResource() {
        CloudFoundryResourceProvider provider = new CloudFoundryResourceProvider();

        Resource resource = provider.createResource(EmptyConfigProperties.INSTANCE);

        assertThat(provider.order()).isZero();
        assertThat(resource).isSameAs(CloudFoundryResource.get());
        assertCloudFoundryAttributes(resource);
    }

    @Test
    void resourceProviderServiceLoaderEntryDiscoversProvider() {
        List<ResourceProvider> providers = ServiceLoader.load(
                        ResourceProvider.class, Opentelemetry_cloudfoundry_resourcesTest.class.getClassLoader())
                .stream()
                .filter(provider -> provider.type().getName().equals(CLOUD_FOUNDRY_PROVIDER_NAME))
                .map(ServiceLoader.Provider::get)
                .toList();

        assertThat(providers).hasSize(1);
        assertCloudFoundryAttributes(providers.get(0).createResource(EmptyConfigProperties.INSTANCE));
    }

    @Test
    void cloudFoundryResourceDetectorExposesDeclarativeComponentProviderContract() {
        CloudFoundryResourceDetector detector = new CloudFoundryResourceDetector();

        assertThat(detector.getType()).isEqualTo(Resource.class);
        assertThat(detector.getName()).isEqualTo("cloud_foundry");
        assertThat(detector.create(DeclarativeConfigProperties.empty())).isSameAs(CloudFoundryResource.get());
        assertCloudFoundryAttributes(detector.create(DeclarativeConfigProperties.empty()));
    }

    @Test
    void componentProviderServiceLoaderEntryDiscoversDetector() {
        List<ComponentProvider> providers = ServiceLoader.load(
                        ComponentProvider.class, Opentelemetry_cloudfoundry_resourcesTest.class.getClassLoader())
                .stream()
                .filter(provider -> provider.type().getName().equals(CloudFoundryResourceDetector.class.getName()))
                .map(ServiceLoader.Provider::get)
                .toList();

        assertThat(providers).hasSize(1);
        ComponentProvider provider = providers.get(0);
        assertThat(provider.getType()).isEqualTo(Resource.class);
        assertThat(provider.getName()).isEqualTo("cloud_foundry");
        assertCloudFoundryAttributes((Resource) provider.create(DeclarativeConfigProperties.empty()));
    }

    @Test
    void autoconfigurationMergesCloudFoundryProviderWhenItIsEnabled() {
        AtomicReference<Resource> configuredResource = new AtomicReference<>();
        AutoConfiguredOpenTelemetrySdk configured = null;

        try {
            configured = AutoConfiguredOpenTelemetrySdk.builder()
                    .disableShutdownHook()
                    .setServiceClassLoader(Opentelemetry_cloudfoundry_resourcesTest.class.getClassLoader())
                    .addPropertiesSupplier(() -> Map.of(
                            "otel.sdk.disabled", "true",
                            "otel.java.enabled.resource.providers", CLOUD_FOUNDRY_PROVIDER_NAME))
                    .addResourceCustomizer((resource, config) -> {
                        configuredResource.set(resource);
                        return resource;
                    })
                    .build();

            assertCloudFoundryAttributes(configuredResource.get());
        } finally {
            if (configured != null) {
                configured.getOpenTelemetrySdk().close();
            }
        }
    }

    @Test
    void autoconfigurationDiscoversCloudFoundryProviderByDefault() {
        AtomicReference<Resource> configuredResource = new AtomicReference<>();
        AutoConfiguredOpenTelemetrySdk configured = null;

        try {
            configured = AutoConfiguredOpenTelemetrySdk.builder()
                    .disableShutdownHook()
                    .setServiceClassLoader(Opentelemetry_cloudfoundry_resourcesTest.class.getClassLoader())
                    .addPropertiesSupplier(() -> Map.of("otel.sdk.disabled", "true"))
                    .addResourceCustomizer((resource, config) -> {
                        configuredResource.set(resource);
                        return resource;
                    })
                    .build();

            assertCloudFoundryAttributes(configuredResource.get());
        } finally {
            if (configured != null) {
                configured.getOpenTelemetrySdk().close();
            }
        }
    }

    @Test
    void autoconfigurationSkipsCloudFoundryProviderWhenItIsDisabled() {
        AtomicReference<Resource> configuredResource = new AtomicReference<>();
        AutoConfiguredOpenTelemetrySdk configured = null;

        try {
            configured = AutoConfiguredOpenTelemetrySdk.builder()
                    .disableShutdownHook()
                    .setServiceClassLoader(Opentelemetry_cloudfoundry_resourcesTest.class.getClassLoader())
                    .addPropertiesSupplier(() -> Map.of(
                            "otel.sdk.disabled", "true",
                            "otel.java.disabled.resource.providers", CLOUD_FOUNDRY_PROVIDER_NAME))
                    .addResourceCustomizer((resource, config) -> {
                        configuredResource.set(resource);
                        return resource;
                    })
                    .build();

            assertNoCloudFoundryAttributes(configuredResource.get());
        } finally {
            if (configured != null) {
                configured.getOpenTelemetrySdk().close();
            }
        }
    }

    private static void assertCloudFoundryAttributes(Resource resource) {
        assertThat(resource).isNotNull();
        assertThat(resource.getAttribute(CLOUDFOUNDRY_APP_ID)).isEqualTo("app-id-123");
        assertThat(resource.getAttribute(CLOUDFOUNDRY_APP_INSTANCE_ID)).isEqualTo("7");
        assertThat(resource.getAttribute(CLOUDFOUNDRY_APP_NAME)).isEqualTo("orders-service");
        assertThat(resource.getAttribute(CLOUDFOUNDRY_ORG_ID)).isEqualTo("org-id-456");
        assertThat(resource.getAttribute(CLOUDFOUNDRY_ORG_NAME)).isEqualTo("payments-org");
        assertThat(resource.getAttribute(CLOUDFOUNDRY_PROCESS_ID)).isEqualTo("process-id-789");
        assertThat(resource.getAttribute(CLOUDFOUNDRY_PROCESS_TYPE)).isEqualTo("web");
        assertThat(resource.getAttribute(CLOUDFOUNDRY_SPACE_ID)).isEqualTo("space-id-abc");
        assertThat(resource.getAttribute(CLOUDFOUNDRY_SPACE_NAME)).isEqualTo("prod-space");
    }

    private static void assertNoCloudFoundryAttributes(Resource resource) {
        assertThat(resource).isNotNull();
        assertThat(resource.getAttribute(CLOUDFOUNDRY_APP_ID)).isNull();
        assertThat(resource.getAttribute(CLOUDFOUNDRY_APP_INSTANCE_ID)).isNull();
        assertThat(resource.getAttribute(CLOUDFOUNDRY_APP_NAME)).isNull();
        assertThat(resource.getAttribute(CLOUDFOUNDRY_ORG_ID)).isNull();
        assertThat(resource.getAttribute(CLOUDFOUNDRY_ORG_NAME)).isNull();
        assertThat(resource.getAttribute(CLOUDFOUNDRY_PROCESS_ID)).isNull();
        assertThat(resource.getAttribute(CLOUDFOUNDRY_PROCESS_TYPE)).isNull();
        assertThat(resource.getAttribute(CLOUDFOUNDRY_SPACE_ID)).isNull();
        assertThat(resource.getAttribute(CLOUDFOUNDRY_SPACE_NAME)).isNull();
    }

    private enum EmptyConfigProperties implements ConfigProperties {
        INSTANCE;

        @Override
        public String getString(String name) {
            return null;
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
            return Collections.emptyMap();
        }
    }
}
