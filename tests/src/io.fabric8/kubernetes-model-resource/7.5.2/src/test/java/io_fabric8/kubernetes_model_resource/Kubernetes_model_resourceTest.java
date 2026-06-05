/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_resource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.resource.v1.AllocatedDeviceStatus;
import io.fabric8.kubernetes.api.model.resource.v1.AllocatedDeviceStatusBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.AllocationResult;
import io.fabric8.kubernetes.api.model.resource.v1.AllocationResultBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.CapacityRequestPolicy;
import io.fabric8.kubernetes.api.model.resource.v1.CapacityRequestPolicyBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.CapacityRequestPolicyRangeBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.CapacityRequirements;
import io.fabric8.kubernetes.api.model.resource.v1.CapacityRequirementsBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.Counter;
import io.fabric8.kubernetes.api.model.resource.v1.CounterBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.CounterSet;
import io.fabric8.kubernetes.api.model.resource.v1.CounterSetBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.Device;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceAllocationConfiguration;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceAllocationConfigurationBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceAllocationResult;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceAllocationResultBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceAttribute;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceAttributeBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceCapacity;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceCapacityBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceClaim;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceClaimBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceClaimConfiguration;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceClaimConfigurationBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceClass;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceClassBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceClassConfiguration;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceClassConfigurationBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceClassList;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceClassListBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceClassSpec;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceClassSpecBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceConstraint;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceConstraintBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceCounterConsumption;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceCounterConsumptionBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceRequest;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceRequestAllocationResult;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceRequestAllocationResultBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceRequestBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceSelector;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceSelectorBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceTaint;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceTaintBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceToleration;
import io.fabric8.kubernetes.api.model.resource.v1.DeviceTolerationBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ExactDeviceRequest;
import io.fabric8.kubernetes.api.model.resource.v1.ExactDeviceRequestBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.NetworkDeviceData;
import io.fabric8.kubernetes.api.model.resource.v1.NetworkDeviceDataBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.OpaqueDeviceConfiguration;
import io.fabric8.kubernetes.api.model.resource.v1.OpaqueDeviceConfigurationBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaim;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimConsumerReference;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimConsumerReferenceBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimList;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimListBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimSpec;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimSpecBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimStatus;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimStatusBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimTemplate;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimTemplateBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimTemplateList;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimTemplateListBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceClaimTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ResourcePool;
import io.fabric8.kubernetes.api.model.resource.v1.ResourcePoolBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceSlice;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceSliceBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceSliceList;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceSliceListBuilder;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceSliceSpec;
import io.fabric8.kubernetes.api.model.resource.v1.ResourceSliceSpecBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class Kubernetes_model_resourceTest {
    private static final String API_VERSION = "resource.k8s.io/v1";
    private static final String DRIVER = "gpu.example.com";

    @Test
    void resourceClaimComposesRequestsConstraintsAllocationAndReservedConsumers() {
        DeviceSelector selector = new DeviceSelectorBuilder()
                .withNewCel("device.attributes[\"vendor\"].string == \"acme\"")
                .build();
        DeviceToleration toleration = new DeviceTolerationBuilder()
                .withKey("dedicated")
                .withOperator("Equal")
                .withValue("accelerator")
                .withEffect("NoSchedule")
                .withTolerationSeconds(30L)
                .build();
        CapacityRequirements capacity = new CapacityRequirementsBuilder()
                .addToRequests("memory", new Quantity("16", "Gi"))
                .build();
        ExactDeviceRequest exactRequest = new ExactDeviceRequestBuilder()
                .withDeviceClassName(DRIVER)
                .withAllocationMode("ExactCount")
                .withCount(2L)
                .withAdminAccess(Boolean.FALSE)
                .withCapacity(capacity)
                .withSelectors(selector)
                .withTolerations(toleration)
                .build();
        DeviceRequest request = new DeviceRequestBuilder()
                .withName("accelerator")
                .withExactly(exactRequest)
                .build();
        DeviceConstraint constraint = new DeviceConstraintBuilder()
                .withRequests("accelerator")
                .withMatchAttribute("vendor")
                .build();
        OpaqueDeviceConfiguration opaque = new OpaqueDeviceConfigurationBuilder()
                .withDriver(DRIVER)
                .withParameters(Map.of("mode", "exclusive"))
                .build();
        DeviceClaimConfiguration configuration = new DeviceClaimConfigurationBuilder()
                .withRequests("accelerator")
                .withOpaque(opaque)
                .build();
        DeviceClaim devices = new DeviceClaimBuilder()
                .withRequests(request)
                .withConstraints(constraint)
                .withConfig(configuration)
                .build();
        ResourceClaimSpec spec = new ResourceClaimSpecBuilder()
                .withDevices(devices)
                .build();

        ResourceClaimStatus status = createAllocatedStatus();
        ResourceClaim claim = new ResourceClaimBuilder()
                .withApiVersion(API_VERSION)
                .withKind("ResourceClaim")
                .withMetadata(new ObjectMetaBuilder()
                        .withName("gpu-claim")
                        .withNamespace("workloads")
                        .addToLabels("app", "renderer")
                        .build())
                .withSpec(spec)
                .withStatus(status)
                .addToAdditionalProperties("managedBy", "fabric8-test")
                .build();

        assertThat(claim).isInstanceOf(HasMetadata.class).isInstanceOf(Namespaced.class);
        assertThat(claim.getApiVersion()).isEqualTo(API_VERSION);
        assertThat(claim.getKind()).isEqualTo("ResourceClaim");
        assertThat(claim.getMetadata().getNamespace()).isEqualTo("workloads");
        assertThat(claim.getSpec().getDevices().getRequests()).hasSize(1);
        assertThat(claim.getSpec().getDevices().getRequests().get(0).getExactly().getSelectors().get(0)
                .getCel().getExpression()).contains("vendor");
        assertThat(claim.getSpec().getDevices().getConstraints().get(0).getMatchAttribute()).isEqualTo("vendor");
        assertThat(claim.getSpec().getDevices().getConfig().get(0).getOpaque().getParameters())
                .isEqualTo(Map.of("mode", "exclusive"));
        assertThat(claim.getStatus().getAllocation().getDevices().getResults().get(0).getConsumedCapacity())
                .containsEntry("memory", new Quantity("8", "Gi"));
        assertThat(claim.getStatus().getDevices().get(0).getNetworkData().getIps())
                .containsExactly("192.0.2.10", "2001:db8::10");
        assertThat(claim.getStatus().getReservedFor().get(0).getResource()).isEqualTo("pods");
        assertThat(claim.getAdditionalProperties()).containsEntry("managedBy", "fabric8-test");

        ResourceClaim renamed = claim.edit()
                .withMetadata(new ObjectMetaBuilder(claim.getMetadata()).withName("gpu-claim-copy").build())
                .build();
        ResourceClaimList list = new ResourceClaimListBuilder()
                .withApiVersion(API_VERSION)
                .withKind("ResourceClaimList")
                .withItems(claim, renamed)
                .build();

        assertThat(renamed).isNotEqualTo(claim);
        assertThat(new ResourceClaimBuilder(claim).build()).isEqualTo(claim);
        assertThat(list.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("gpu-claim", "gpu-claim-copy");
    }

    @Test
    void resourceSliceModelsDevicesCountersPoolsAndLists() {
        DeviceAttribute vendor = new DeviceAttributeBuilder().withString("acme").build();
        DeviceAttribute numaNode = new DeviceAttributeBuilder().withInt(1L).build();
        CapacityRequestPolicy memoryPolicy = new CapacityRequestPolicyBuilder()
                .withNewDefault("4", "Gi")
                .addNewValidValue("4", "Gi")
                .addNewValidValue("8", "Gi")
                .withValidRange(new CapacityRequestPolicyRangeBuilder()
                        .withNewMin("4", "Gi")
                        .withNewMax("16", "Gi")
                        .withNewStep("4", "Gi")
                        .build())
                .build();
        DeviceCapacity memory = new DeviceCapacityBuilder()
                .withNewValue("16", "Gi")
                .withRequestPolicy(memoryPolicy)
                .build();
        Counter counter = new CounterBuilder().withNewValue("100").build();
        CounterSet sharedCounters = new CounterSetBuilder()
                .withName("quota")
                .addToCounters("shares", counter)
                .build();
        DeviceCounterConsumption counterConsumption = new DeviceCounterConsumptionBuilder()
                .withCounterSet("quota")
                .addToCounters("shares", new CounterBuilder().withNewValue("25").build())
                .build();
        DeviceTaint taint = new DeviceTaintBuilder()
                .withKey("example.com/gpu")
                .withValue("true")
                .withEffect("NoSchedule")
                .withTimeAdded("2026-01-01T00:00:00Z")
                .build();
        Device device = new DeviceBuilder()
                .withName("gpu0")
                .withAllNodes(Boolean.FALSE)
                .withBindsToNode(Boolean.TRUE)
                .withNodeName("node-a")
                .addToAttributes("vendor", vendor)
                .addToAttributes("numaNode", numaNode)
                .addToCapacity("memory", memory)
                .withBindingConditions("Ready")
                .withBindingFailureConditions("AllocationFailed")
                .withConsumesCounters(counterConsumption)
                .withTaints(taint)
                .build();
        ResourcePool pool = new ResourcePoolBuilder()
                .withName("pool-a")
                .withGeneration(7L)
                .withResourceSliceCount(2L)
                .build();
        ResourceSliceSpec spec = new ResourceSliceSpecBuilder()
                .withDriver(DRIVER)
                .withPool(pool)
                .withNodeName("node-a")
                .withPerDeviceNodeSelection(Boolean.TRUE)
                .withDevices(device)
                .withSharedCounters(sharedCounters)
                .build();
        ResourceSlice slice = new ResourceSliceBuilder()
                .withApiVersion(API_VERSION)
                .withKind("ResourceSlice")
                .withMetadata(new ObjectMetaBuilder().withName("slice-a").build())
                .withSpec(spec)
                .build();

        assertThat(slice.getSpec().getDriver()).isEqualTo(DRIVER);
        assertThat(slice.getSpec().getPool().getGeneration()).isEqualTo(7L);
        assertThat(slice.getSpec().getDevices()).hasSize(1);
        assertThat(slice.getSpec().getDevices().get(0).getAttributes()).containsEntry("vendor", vendor);
        assertThat(slice.getSpec().getDevices().get(0).getCapacity().get("memory").getRequestPolicy()
                .getValidRange().getMax()).isEqualTo(new Quantity("16", "Gi"));
        assertThat(slice.getSpec().getDevices().get(0).getBindingConditions()).containsExactly("Ready");
        assertThat(slice.getSpec().getDevices().get(0).getConsumesCounters().get(0).getCounters())
                .containsKey("shares");
        assertThat(slice.getSpec().getSharedCounters().get(0).getCounters()).containsEntry("shares", counter);

        ResourceSlice changed = slice.edit()
                .withSpec(new ResourceSliceSpecBuilder(slice.getSpec())
                        .withDevices(new DeviceBuilder(device).withNodeName("node-b").build())
                        .build())
                .build();
        ResourceSliceList list = new ResourceSliceListBuilder()
                .withApiVersion(API_VERSION)
                .withKind("ResourceSliceList")
                .withItems(slice, changed)
                .build();

        assertThat(changed.getSpec().getDevices().get(0).getNodeName()).isEqualTo("node-b");
        assertThat(slice.toString()).contains("slice-a");
        assertThat(list.getItems()).hasSize(2);
    }

    @Test
    void deviceClassCapturesSelectionAndDriverConfiguration() {
        DeviceSelector selector = new DeviceSelectorBuilder()
                .withNewCel("device.capacity[\"memory\"].value >= quantity(\"8Gi\")")
                .build();
        DeviceClassConfiguration configuration = new DeviceClassConfigurationBuilder()
                .withNewOpaque(DRIVER, Map.of("profile", "compute", "revision", 2))
                .build();
        DeviceClassSpec spec = new DeviceClassSpecBuilder()
                .withExtendedResourceName("example.com/gpu")
                .withSelectors(selector)
                .withConfig(configuration)
                .build();
        DeviceClass deviceClass = new DeviceClassBuilder()
                .withApiVersion(API_VERSION)
                .withKind("DeviceClass")
                .withMetadata(new ObjectMetaBuilder()
                        .withName("gpu-class")
                        .addToAnnotations("resource.fabric8.io/test", "true")
                        .build())
                .withSpec(spec)
                .build();

        assertThat(deviceClass.getMetadata().getAnnotations()).containsEntry("resource.fabric8.io/test", "true");
        assertThat(deviceClass.getSpec().getExtendedResourceName()).isEqualTo("example.com/gpu");
        assertThat(deviceClass.getSpec().getSelectors().get(0).getCel().getExpression()).contains("quantity");
        assertThat(deviceClass.getSpec().getConfig().get(0).getOpaque().getParameters())
                .isEqualTo(Map.of("profile", "compute", "revision", 2));
        assertThat(new DeviceClassBuilder(deviceClass).build()).hasSameHashCodeAs(deviceClass);

        DeviceClass updated = deviceClass.toBuilder()
                .withSpec(new DeviceClassSpecBuilder(deviceClass.getSpec())
                        .addToAdditionalProperties("validated", Boolean.TRUE)
                        .build())
                .build();
        DeviceClassList list = new DeviceClassListBuilder()
                .withApiVersion(API_VERSION)
                .withKind("DeviceClassList")
                .addToItems(deviceClass)
                .addToItems(updated)
                .build();

        assertThat(updated.getSpec().getAdditionalProperties()).containsEntry("validated", Boolean.TRUE);
        assertThat(list.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("gpu-class", "gpu-class");
    }

    @Test
    void resourceClaimTemplateSeparatesTemplateMetadataFromClaimMetadata() {
        ResourceClaimSpec claimSpec = new ResourceClaimSpecBuilder()
                .addToAdditionalProperties("allocationStrategy", "balanced")
                .build();
        ResourceClaimTemplate template = new ResourceClaimTemplateBuilder()
                .withApiVersion(API_VERSION)
                .withKind("ResourceClaimTemplate")
                .withMetadata(new ObjectMetaBuilder()
                        .withName("gpu-claim-template")
                        .withNamespace("workloads")
                        .addToLabels("template", "true")
                        .build())
                .withNewSpec()
                .withMetadata(new ObjectMetaBuilder()
                        .withGenerateName("gpu-claim-")
                        .addToLabels("tier", "gold")
                        .addToAnnotations("resource.fabric8.io/profile", "interactive")
                        .build())
                .withSpec(claimSpec)
                .endSpec()
                .addToAdditionalProperties("managedBy", "fabric8-test")
                .build();

        assertThat(template).isInstanceOf(HasMetadata.class).isInstanceOf(Namespaced.class);
        assertThat(template.getMetadata().getName()).isEqualTo("gpu-claim-template");
        assertThat(template.getMetadata().getLabels()).containsEntry("template", "true");
        assertThat(template.getSpec().getMetadata().getGenerateName()).isEqualTo("gpu-claim-");
        assertThat(template.getSpec().getMetadata().getLabels()).containsEntry("tier", "gold");
        assertThat(template.getSpec().getMetadata().getAnnotations())
                .containsEntry("resource.fabric8.io/profile", "interactive");
        assertThat(template.getSpec().getSpec().getAdditionalProperties())
                .containsEntry("allocationStrategy", "balanced");
        assertThat(template.getAdditionalProperties()).containsEntry("managedBy", "fabric8-test");

        ResourceClaimTemplate updated = template.toBuilder()
                .withSpec(new ResourceClaimTemplateSpecBuilder(template.getSpec())
                        .withMetadata(new ObjectMetaBuilder(template.getSpec().getMetadata())
                                .addToLabels("workload", "render")
                                .build())
                        .build())
                .build();
        ResourceClaimTemplateList list = new ResourceClaimTemplateListBuilder()
                .withApiVersion(API_VERSION)
                .withKind("ResourceClaimTemplateList")
                .withItems(template, updated)
                .build();

        assertThat(updated.getSpec().getMetadata().getLabels()).containsEntry("workload", "render");
        assertThat(template.getSpec().getMetadata().getLabels()).doesNotContainKey("workload");
        assertThat(list.getItems()).extracting(item -> item.getSpec().getMetadata().getGenerateName())
                .containsExactly("gpu-claim-", "gpu-claim-");
    }

    @Test
    void quantitiesUsedByResourceModelsSupportArithmeticAndComparison() {
        Quantity memory = new Quantity("4", "Gi");
        Quantity doubled = memory.multiply(2);
        Quantity total = doubled.add(new Quantity("1024", "Mi"));

        assertThat(memory.compareTo(new Quantity("4096", "Mi"))).isZero();
        assertThat(doubled).isEqualTo(new Quantity("8", "Gi"));
        assertThat(total.getNumericalAmount()).isEqualByComparingTo(new Quantity("9", "Gi").getNumericalAmount());
        assertThat(Quantity.getAmountInBytes(new Quantity("1", "Ki"))).isEqualByComparingTo("1024");
    }

    private static ResourceClaimStatus createAllocatedStatus() {
        DeviceAllocationConfiguration configuration = new DeviceAllocationConfigurationBuilder()
                .withNewOpaque(DRIVER, Map.of("allocation", "immediate"))
                .build();
        DeviceRequestAllocationResult result = new DeviceRequestAllocationResultBuilder()
                .withRequest("accelerator")
                .withDriver(DRIVER)
                .withPool("pool-a")
                .withDevice("gpu0")
                .withShareID("share-1")
                .withAdminAccess(Boolean.FALSE)
                .withBindingConditions(List.of("Ready"))
                .withBindingFailureConditions(List.of("AllocationFailed"))
                .addToConsumedCapacity("memory", new Quantity("8", "Gi"))
                .withTolerations(new DeviceTolerationBuilder()
                        .withKey("dedicated")
                        .withOperator("Exists")
                        .withEffect("NoSchedule")
                        .build())
                .build();
        DeviceAllocationResult devices = new DeviceAllocationResultBuilder()
                .withConfig(configuration)
                .withResults(result)
                .build();
        AllocationResult allocation = new AllocationResultBuilder()
                .withAllocationTimestamp("2026-01-01T00:00:00Z")
                .withDevices(devices)
                .build();
        NetworkDeviceData networkData = new NetworkDeviceDataBuilder()
                .withInterfaceName("eth0")
                .withHardwareAddress("02:00:00:00:00:01")
                .withIps("192.0.2.10", "2001:db8::10")
                .build();
        AllocatedDeviceStatus deviceStatus = new AllocatedDeviceStatusBuilder()
                .withDriver(DRIVER)
                .withPool("pool-a")
                .withDevice("gpu0")
                .withShareID("share-1")
                .withNetworkData(networkData)
                .withData(Map.of("health", "ok"))
                .build();
        ResourceClaimConsumerReference consumer = new ResourceClaimConsumerReferenceBuilder()
                .withApiGroup("")
                .withResource("pods")
                .withName("renderer")
                .withUid("pod-uid")
                .build();

        return new ResourceClaimStatusBuilder()
                .withAllocation(allocation)
                .withDevices(deviceStatus)
                .withReservedFor(consumer)
                .build();
    }
}
