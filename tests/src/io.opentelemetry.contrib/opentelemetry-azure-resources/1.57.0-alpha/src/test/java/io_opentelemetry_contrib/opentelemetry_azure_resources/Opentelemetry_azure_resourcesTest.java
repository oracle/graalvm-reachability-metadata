/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.opentelemetry.contrib.azure.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class Opentelemetry_azure_resourcesTest {
    private static final String APP_SERVICE_RESOURCE_ID = "/subscriptions/sub-app-123"
            + "/resourceGroups/rg-app/providers/Microsoft.Web/sites/orders-api";
    private static final String VM_RESOURCE_ID = "/subscriptions/sub-1/resourceGroups/rg-vm"
            + "/providers/Microsoft.Compute/virtualMachines/vm-one";
    private static final String VM_METADATA_JSON = """
            {
              "compute": {
                "location": "westeurope",
                "resourceId": "%s",
                "vmId": "vm-guid-123",
                "name": "vm-one",
                "vmSize": "Standard_D2s_v5",
                "osType": "Linux",
                "version": "20.04.202404160",
                "vmScaleSetName": "aks-agentpool-12345678-vmss",
                "sku": "22_04-lts-gen2",
                "ignoredNested": {"value": "not-an-attribute"}
              },
              "network": {"interface": []}
            }
            """.formatted(VM_RESOURCE_ID);

    @Test
    void appServiceResourceUsesSimulatedWebsiteEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("WEBSITE_SITE_NAME", "orders-api");
        env.put("WEBSITE_OWNER_NAME", "sub-app-123+eastuswebspace");
        env.put("WEBSITE_RESOURCE_GROUP", "rg-app");
        env.put("REGION_NAME", "eastus");
        env.put("WEBSITE_SLOT_NAME", "staging");
        env.put("WEBSITE_HOSTNAME", "orders-api.azurewebsites.net");
        env.put("WEBSITE_INSTANCE_ID", "app-instance-1");
        env.put("WEBSITE_HOME_STAMPNAME", "eastus-stamp-7");

        Resource resource = new AzureAppServiceResourceProvider(env).createResource();

        assertAzureCloud(resource, "azure.app_service");
        assertThat(attribute(resource, "service.name")).isEqualTo("orders-api");
        assertThat(attribute(resource, "cloud.region")).isEqualTo("eastus");
        assertThat(attribute(resource, "deployment.environment.name")).isEqualTo("staging");
        assertThat(attribute(resource, "host.id")).isEqualTo("orders-api.azurewebsites.net");
        assertThat(attribute(resource, "service.instance.id")).isEqualTo("app-instance-1");
        assertThat(attribute(resource, "azure.app.service.stamp")).isEqualTo("eastus-stamp-7");
        assertThat(attribute(resource, "cloud.resource_id")).isEqualTo(APP_SERVICE_RESOURCE_ID);
    }

    @Test
    void appServiceResourceIsEmptyWhenWebsiteEnvironmentIsAbsent() {
        Map<String, String> env = Map.of("REGION_NAME", "eastus");

        Resource resource = new AzureAppServiceResourceProvider(env).createResource();

        assertThat(attribute(resource, "cloud.provider")).isNull();
        assertThat(attribute(resource, "cloud.platform")).isNull();
    }

    @Test
    void functionsResourceUsesSimulatedFunctionsEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("WEBSITE_SITE_NAME", "billing-functions");
        env.put("FUNCTIONS_EXTENSION_VERSION", "~4");
        env.put("WEBSITE_INSTANCE_ID", "function-worker-42");
        env.put("WEBSITE_MEMORY_LIMIT_MB", "1536");
        env.put("REGION_NAME", "northeurope");

        Resource resource = new AzureFunctionsResourceProvider(env).createResource();

        assertAzureCloud(resource, "azure.functions");
        assertThat(attribute(resource, "cloud.region")).isEqualTo("northeurope");
        assertThat(attribute(resource, "faas.name")).isEqualTo("billing-functions");
        assertThat(attribute(resource, "faas.version")).isEqualTo("~4");
        assertThat(attribute(resource, "faas.instance")).isEqualTo("function-worker-42");
        assertThat(attribute(resource, AttributeKey.longKey("faas.max_memory"))).isEqualTo(1536L);
    }

    @Test
    void containerAppsResourceUsesSimulatedContainerAppEnvironment() {
        Map<String, String> env = Map.of(
                "CONTAINER_APP_NAME", "inventory-api",
                "CONTAINER_APP_REPLICA_NAME", "inventory-api--blue-5d9b7f8f6c-p4w9x",
                "CONTAINER_APP_REVISION", "inventory-api--blue");

        Resource resource = new AzureContainersResourceProvider(env).createResource();

        assertAzureCloud(resource, "azure.container_apps");
        assertThat(attribute(resource, "service.name")).isEqualTo("inventory-api");
        assertThat(attribute(resource, "service.instance.id"))
                .isEqualTo("inventory-api--blue-5d9b7f8f6c-p4w9x");
        assertThat(attribute(resource, "service.version")).isEqualTo("inventory-api--blue");
    }

    @Test
    void environmentPlatformDetectionPrefersMostSpecificAzureEnvironment() {
        assertThat(AzureEnvVarPlatform.detect(Map.of(
                        "CONTAINER_APP_NAME", "cart-api",
                        "WEBSITE_SITE_NAME", "cart-site",
                        "FUNCTIONS_EXTENSION_VERSION", "~4")))
                .isEqualTo(AzureEnvVarPlatform.CONTAINER_APP);
        assertThat(AzureEnvVarPlatform.detect(Map.of(
                        "WEBSITE_SITE_NAME", "cart-site",
                        "FUNCTIONS_EXTENSION_VERSION", "~4")))
                .isEqualTo(AzureEnvVarPlatform.FUNCTIONS);
        assertThat(AzureEnvVarPlatform.detect(Map.of("WEBSITE_SITE_NAME", "cart-site")))
                .isEqualTo(AzureEnvVarPlatform.APP_SERVICE);
        assertThat(AzureEnvVarPlatform.detect(Map.of("REGION_NAME", "eastus")))
                .isEqualTo(AzureEnvVarPlatform.NONE);
    }

    @Test
    void vmResourceUsesSuppliedInstanceMetadataJson() {
        Supplier<Optional<String>> metadataSupplier = () -> Optional.of(VM_METADATA_JSON);

        Resource resource = new AzureVmResourceProvider(metadataSupplier).createResource();

        assertAzureCloud(resource, "azure.vm");
        assertThat(attribute(resource, "cloud.region")).isEqualTo("westeurope");
        assertThat(attribute(resource, "cloud.resource_id")).isEqualTo(VM_RESOURCE_ID);
        assertThat(attribute(resource, "host.id")).isEqualTo("vm-guid-123");
        assertThat(attribute(resource, "host.name")).isEqualTo("vm-one");
        assertThat(attribute(resource, "host.type")).isEqualTo("Standard_D2s_v5");
        assertThat(attribute(resource, "os.type")).isEqualTo("Linux");
        assertThat(attribute(resource, "os.version")).isEqualTo("20.04.202404160");
        assertThat(attribute(resource, "azure.vm.scaleset.name"))
                .isEqualTo("aks-agentpool-12345678-vmss");
        assertThat(attribute(resource, "azure.vm.sku")).isEqualTo("22_04-lts-gen2");
    }

    @Test
    void vmResourceIsEmptyWhenSuppliedMetadataIsUnavailable() {
        Resource resource = new AzureVmResourceProvider(Optional::empty).createResource();

        assertThat(attribute(resource, "cloud.provider")).isNull();
        assertThat(attribute(resource, "host.name")).isNull();
    }

    @Test
    void aksResourceUsesSuppliedInstanceMetadataJsonWhenKubernetesEnvironmentIsPresent() {
        String metadataJson = """
                {
                  "compute": {
                    "resourceGroupName": "MC_platform-rg_orders-cluster_westeurope",
                    "name": "aks-nodepool1-12345678-vmss000001"
                  }
                }
                """;
        Map<String, String> env = Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1");
        Supplier<Optional<String>> metadataSupplier = () -> Optional.of(metadataJson);

        Resource resource = new AzureAksResourceProvider(metadataSupplier, env).createResource();

        assertAzureCloud(resource, "azure.aks");
        assertThat(attribute(resource, "k8s.cluster.name")).isEqualTo("orders-cluster");
    }

    @Test
    void aksResourceDoesNotQueryMetadataOutsideKubernetes() {
        Supplier<Optional<String>> failingSupplier = () -> {
            throw new AssertionError(
                    "metadata supplier should not be called without KUBERNETES_SERVICE_HOST");
        };

        Resource resource = new AzureAksResourceProvider(failingSupplier, Map.of())
                .createResource();

        assertThat(attribute(resource, "cloud.provider")).isNull();
        assertThat(attribute(resource, "k8s.cluster.name")).isNull();
    }

    @Test
    void metadataBackedResourceProvidersRunAfterEnvironmentResourceProviders() {
        AzureAppServiceResourceProvider environmentProvider = new AzureAppServiceResourceProvider();
        AzureVmResourceProvider vmProvider = new AzureVmResourceProvider();
        AzureAksResourceProvider aksProvider = new AzureAksResourceProvider();

        assertThat(vmProvider.order()).isGreaterThan(environmentProvider.order());
        assertThat(aksProvider.order()).isEqualTo(vmProvider.order());
    }

    @Test
    void cloudResourceProvidersSkipWhenAnotherCloudProviderAlreadyExists() {
        Map<String, String> env = Map.of("WEBSITE_SITE_NAME", "app");
        AzureAppServiceResourceProvider provider = new AzureAppServiceResourceProvider(env);
        Resource emptyResource = Resource.empty();
        Resource existingCloudResource = Resource.create(
                Attributes.of(AttributeKey.stringKey("cloud.provider"), "aws"));

        Resource createdResource = provider.createResource(null);

        assertThat(provider.shouldApply(null, emptyResource)).isTrue();
        assertThat(provider.shouldApply(null, existingCloudResource)).isFalse();
        assertThat(createdResource.getAttribute(AttributeKey.stringKey("service.name")))
                .isEqualTo("app");
    }

    @Test
    void serviceLoaderDiscoversAutoconfigureProviders() {
        Set<String> resourceProviderNames = new HashSet<>();
        for (ResourceProvider provider : ServiceLoader.load(ResourceProvider.class)) {
            resourceProviderNames.add(provider.getClass().getName());
        }

        assertThat(resourceProviderNames)
                .contains(
                        AzureAppServiceResourceProvider.class.getName(),
                        AzureFunctionsResourceProvider.class.getName(),
                        AzureVmResourceProvider.class.getName());

        Set<String> componentProviderNames = new HashSet<>();
        for (ComponentProvider provider : ServiceLoader.load(ComponentProvider.class)) {
            componentProviderNames.add(provider.getName() + ":" + provider.getType().getName());
        }

        assertThat(componentProviderNames).contains("azure:" + Resource.class.getName());
    }

    @Test
    void azureResourceDetectorAdvertisesAutoconfigureComponentIdentity() {
        AzureResourceDetector detector = new AzureResourceDetector();

        assertThat(detector.getName()).isEqualTo("azure");
        assertThat(detector.getType()).isEqualTo(Resource.class);
    }

    private static void assertAzureCloud(Resource resource, String platform) {
        assertThat(attribute(resource, "cloud.provider")).isEqualTo("azure");
        assertThat(attribute(resource, "cloud.platform")).isEqualTo(platform);
    }

    private static String attribute(Resource resource, String key) {
        return attribute(resource, AttributeKey.stringKey(key));
    }

    private static <T> T attribute(Resource resource, AttributeKey<T> key) {
        return resource.getAttribute(key);
    }
}
