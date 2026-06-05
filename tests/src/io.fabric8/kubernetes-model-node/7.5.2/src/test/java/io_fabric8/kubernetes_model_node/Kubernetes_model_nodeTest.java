/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_node;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ListMetaBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.node.v1.Overhead;
import io.fabric8.kubernetes.api.model.node.v1.OverheadBuilder;
import io.fabric8.kubernetes.api.model.node.v1.RuntimeClass;
import io.fabric8.kubernetes.api.model.node.v1.RuntimeClassBuilder;
import io.fabric8.kubernetes.api.model.node.v1.RuntimeClassList;
import io.fabric8.kubernetes.api.model.node.v1.RuntimeClassListBuilder;
import io.fabric8.kubernetes.api.model.node.v1.Scheduling;
import io.fabric8.kubernetes.api.model.node.v1.SchedulingBuilder;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class Kubernetes_model_nodeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildsEditsAndSerializesStableRuntimeClass() throws Exception {
        RuntimeClass runtimeClass = new RuntimeClassBuilder()
                .withNewMetadata()
                    .withName("kata")
                    .addToLabels("runtime", "isolated")
                .endMetadata()
                .withHandler("kata-qemu")
                .withNewOverhead()
                    .addToPodFixed("cpu", new Quantity("125m"))
                    .addToPodFixed("memory", new Quantity("128Mi"))
                .endOverhead()
                .withNewScheduling()
                    .addToNodeSelector("node.kubernetes.io/runtime", "kata")
                    .addNewToleration("NoSchedule", "runtime", "Equal", 3600L, "kata")
                .endScheduling()
                .addToAdditionalProperties("x-extension", Map.of("enabled", true))
                .build();

        assertThat(runtimeClass.getApiVersion()).isEqualTo("node.k8s.io/v1");
        assertThat(runtimeClass.getKind()).isEqualTo("RuntimeClass");
        assertThat(runtimeClass.getMetadata().getLabels()).containsEntry("runtime", "isolated");
        assertThat(runtimeClass.getOverhead().getPodFixed().get("cpu").getNumericalAmount())
                .isEqualByComparingTo(new BigDecimal("0.125"));
        assertThat(runtimeClass.getScheduling().getTolerations())
                .extracting(Toleration::getValue)
                .containsExactly("kata");

        RuntimeClass edited = runtimeClass.edit()
                .editMetadata()
                    .addToAnnotations("runtime.fabric8.io/managed", "true")
                .endMetadata()
                .editOverhead()
                    .addToPodFixed("ephemeral-storage", new Quantity("1Gi"))
                .endOverhead()
                .editScheduling()
                    .removeFromNodeSelector("node.kubernetes.io/runtime")
                    .addToNodeSelector("accelerator", "none")
                .endScheduling()
                .build();

        assertThat(runtimeClass.getMetadata().getAnnotations()).isEmpty();
        assertThat(edited.getMetadata().getAnnotations()).containsEntry("runtime.fabric8.io/managed", "true");
        assertThat(edited.getScheduling().getNodeSelector()).containsExactlyEntriesOf(Map.of("accelerator", "none"));
        assertThat(edited.toBuilder().build()).isEqualTo(edited);
        assertThat(edited.toBuilder().build().hashCode()).isEqualTo(edited.hashCode());

        String json = MAPPER.writeValueAsString(edited);
        RuntimeClass roundTripped = MAPPER.readValue(json, RuntimeClass.class);

        assertThat(roundTripped).isEqualTo(edited);
        assertThat(roundTripped.getOverhead().getPodFixed().get("ephemeral-storage").toString()).isEqualTo("1Gi");
        assertThat(roundTripped.getAdditionalProperties()).containsKey("x-extension");
    }

    @Test
    void buildsAndSerializesRuntimeClassLists() throws Exception {
        RuntimeClass kata = new RuntimeClassBuilder()
                .withNewMetadata()
                    .withName("kata")
                .endMetadata()
                .withHandler("kata-qemu")
                .build();

        RuntimeClassList list = new RuntimeClassListBuilder()
                .withMetadata(new ListMetaBuilder()
                        .withResourceVersion("rv-1")
                        .withRemainingItemCount(2L)
                        .build())
                .addNewItem()
                    .withNewMetadata()
                        .withName("native")
                    .endMetadata()
                    .withHandler("runc")
                .endItem()
                .addToItems(kata)
                .build();

        assertThat(list.getApiVersion()).isEqualTo("node.k8s.io/v1");
        assertThat(list.getKind()).isEqualTo("RuntimeClassList");
        assertThat(list.getMetadata().getResourceVersion()).isEqualTo("rv-1");
        assertThat(list.getItems()).extracting(RuntimeClass::getHandler).containsExactly("runc", "kata-qemu");

        RuntimeClassList edited = list.edit()
                .editFirstItem()
                    .withHandler("runc-v2")
                .endItem()
                .build();

        String json = MAPPER.writeValueAsString(edited);
        RuntimeClassList roundTripped = MAPPER.readValue(json, RuntimeClassList.class);

        assertThat(roundTripped).isEqualTo(edited);
        assertThat(roundTripped.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("native", "kata");
    }

    @Test
    void filtersAndUpdatesRuntimeClassListItemsWithPredicates() {
        RuntimeClass nativeRuntime = new RuntimeClassBuilder()
                .withNewMetadata()
                    .withName("native")
                .endMetadata()
                .withHandler("runc")
                .build();
        RuntimeClass kata = new RuntimeClassBuilder()
                .withNewMetadata()
                    .withName("kata")
                .endMetadata()
                .withHandler("kata-qemu")
                .build();
        RuntimeClass gvisor = new RuntimeClassBuilder()
                .withNewMetadata()
                    .withName("gvisor")
                .endMetadata()
                .withHandler("runsc")
                .build();

        RuntimeClassListBuilder builder = new RuntimeClassListBuilder()
                .withItems(nativeRuntime, kata, gvisor);

        assertThat(builder.hasItems()).isTrue();
        assertThat(builder.hasMatchingItem(item -> "kata".equals(item.buildMetadata().getName()))).isTrue();
        assertThat(builder.buildMatchingItem(item -> "runsc".equals(item.getHandler())).getMetadata().getName())
                .isEqualTo("gvisor");

        RuntimeClassList edited = builder
                .setToItems(0, new RuntimeClassBuilder(nativeRuntime).withHandler("runc-v2").build())
                .editMatchingItem(item -> "kata".equals(item.buildMetadata().getName()))
                    .withHandler("kata-qemu-v2")
                .endItem()
                .removeMatchingFromItems(item -> "runsc".equals(item.getHandler()))
                .addNewItemLike(gvisor)
                    .withHandler("runsc-v2")
                .endItem()
                .build();

        assertThat(edited.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("native", "kata", "gvisor");
        assertThat(edited.getItems()).extracting(RuntimeClass::getHandler)
                .containsExactly("runc-v2", "kata-qemu-v2", "runsc-v2");
    }

    @Test
    void buildsAndSerializesBetaRuntimeClass() throws Exception {
        io.fabric8.kubernetes.api.model.node.v1beta1.RuntimeClass runtimeClass =
                new io.fabric8.kubernetes.api.model.node.v1beta1.RuntimeClassBuilder()
                        .withNewMetadata()
                            .withName("sandboxed")
                        .endMetadata()
                        .withHandler("runsc")
                        .withNewOverhead()
                            .addToPodFixed("memory", new Quantity("64Mi"))
                        .endOverhead()
                        .withNewScheduling()
                            .addToNodeSelector("sandbox.gke.io/runtime", "gvisor")
                            .addNewToleration("NoSchedule", "sandbox", "Equal", 120L, "true")
                        .endScheduling()
                        .addToAdditionalProperties("beta-note", "available")
                        .build();

        assertThat(runtimeClass.getApiVersion()).isEqualTo("node.k8s.io/v1beta1");
        assertThat(runtimeClass.getKind()).isEqualTo("RuntimeClass");
        assertThat(runtimeClass.getOverhead().getPodFixed().get("memory").toString()).isEqualTo("64Mi");
        assertThat(runtimeClass.getScheduling().getNodeSelector())
                .containsEntry("sandbox.gke.io/runtime", "gvisor");

        io.fabric8.kubernetes.api.model.node.v1beta1.RuntimeClass copied = runtimeClass.toBuilder()
                .withHandler("runsc-v2")
                .build();
        String json = MAPPER.writeValueAsString(copied);
        io.fabric8.kubernetes.api.model.node.v1beta1.RuntimeClass roundTripped = MAPPER.readValue(
                json, io.fabric8.kubernetes.api.model.node.v1beta1.RuntimeClass.class);

        assertThat(roundTripped).isEqualTo(copied);
        assertThat(roundTripped.getAdditionalProperties()).containsEntry("beta-note", "available");
    }

    @Test
    void buildsAndSerializesAlphaRuntimeClassSpec() throws Exception {
        io.fabric8.kubernetes.api.model.node.v1alpha1.RuntimeClass runtimeClass =
                new io.fabric8.kubernetes.api.model.node.v1alpha1.RuntimeClassBuilder()
                        .withNewMetadata()
                            .withName("legacy-kata")
                        .endMetadata()
                        .withNewSpec()
                            .withRuntimeHandler("kata-runtime")
                            .withNewOverhead()
                                .addToPodFixed("cpu", new Quantity("50m"))
                            .endOverhead()
                            .withNewScheduling()
                                .addToNodeSelector("kubernetes.io/arch", "amd64")
                                .addNewToleration("NoExecute", "runtime", "Equal", 30L, "legacy")
                            .endScheduling()
                        .endSpec()
                        .build();

        assertThat(runtimeClass.getApiVersion()).isEqualTo("node.k8s.io/v1alpha1");
        assertThat(runtimeClass.getKind()).isEqualTo("RuntimeClass");
        assertThat(runtimeClass.getSpec().getRuntimeHandler()).isEqualTo("kata-runtime");
        assertThat(runtimeClass.getSpec().getOverhead().getPodFixed().get("cpu").getNumericalAmount())
                .isEqualByComparingTo(new BigDecimal("0.050"));

        io.fabric8.kubernetes.api.model.node.v1alpha1.RuntimeClass edited = runtimeClass.edit()
                .editSpec()
                    .withRuntimeHandler("kata-runtime-v2")
                    .editScheduling()
                        .addToNodeSelector("topology.kubernetes.io/zone", "test-zone")
                    .endScheduling()
                .endSpec()
                .build();
        String json = MAPPER.writeValueAsString(edited);
        io.fabric8.kubernetes.api.model.node.v1alpha1.RuntimeClass roundTripped = MAPPER.readValue(
                json, io.fabric8.kubernetes.api.model.node.v1alpha1.RuntimeClass.class);

        assertThat(roundTripped).isEqualTo(edited);
        assertThat(roundTripped.getSpec().getScheduling().getNodeSelector())
                .containsEntry("topology.kubernetes.io/zone", "test-zone");
    }

    @Test
    void preservesStandaloneOverheadAndSchedulingExtensions() throws Exception {
        Overhead overhead = new OverheadBuilder()
                .addToPodFixed("cpu", new Quantity("250m"))
                .addToPodFixed("memory", new Quantity("512Mi"))
                .addToAdditionalProperties("node.fabric8.io/profile", Map.of("tier", "sandbox"))
                .build();

        Overhead editedOverhead = overhead.edit()
                .removeFromPodFixed("memory")
                .addToPodFixed("ephemeral-storage", new Quantity("2Gi"))
                .addToAdditionalProperties("node.fabric8.io/enforced", true)
                .build();

        String overheadJson = MAPPER.writeValueAsString(editedOverhead);
        Overhead roundTrippedOverhead = MAPPER.readValue(overheadJson, Overhead.class);

        assertThat(roundTrippedOverhead.getPodFixed()).containsOnlyKeys("cpu", "ephemeral-storage");
        assertThat(roundTrippedOverhead.getPodFixed().get("cpu").getNumericalAmount())
                .isEqualByComparingTo(new BigDecimal("0.250"));
        assertThat(roundTrippedOverhead.getAdditionalProperties())
                .containsEntry("node.fabric8.io/enforced", true)
                .containsKey("node.fabric8.io/profile");

        Scheduling scheduling = new SchedulingBuilder()
                .addToNodeSelector("node.kubernetes.io/instance-type", "sandbox")
                .addToNodeSelector("kubernetes.io/os", "linux")
                .addNewToleration("NoSchedule", "dedicated", "Equal", 600L, "sandbox")
                .addNewToleration("NoExecute", "maintenance", "Equal", 30L, "planned")
                .addToAdditionalProperties("node.fabric8.io/scheduler", Map.of("policy", "strict"))
                .build();

        assertThat(new SchedulingBuilder(scheduling)
                .hasMatchingToleration(toleration -> "maintenance".equals(toleration.getKey()))).isTrue();

        Scheduling editedScheduling = scheduling.edit()
                .removeFromNodeSelector("node.kubernetes.io/instance-type")
                .removeFromTolerations(scheduling.getTolerations().get(0))
                .addToNodeSelector("node-role.kubernetes.io/worker", "true")
                .addToAdditionalProperties("node.fabric8.io/priority", 5)
                .build();

        String schedulingJson = MAPPER.writeValueAsString(editedScheduling);
        Scheduling roundTrippedScheduling = MAPPER.readValue(schedulingJson, Scheduling.class);

        assertThat(roundTrippedScheduling.getNodeSelector())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "kubernetes.io/os", "linux",
                        "node-role.kubernetes.io/worker", "true"));
        assertThat(roundTrippedScheduling.getTolerations())
                .extracting(Toleration::getValue)
                .containsExactly("planned");
        assertThat(roundTrippedScheduling.getAdditionalProperties())
                .containsEntry("node.fabric8.io/priority", 5)
                .containsKey("node.fabric8.io/scheduler");
    }

    @Test
    void serviceLoaderDiscoversNodeModelResources() {
        Set<Class<?>> resourceTypes = new LinkedHashSet<>();
        for (KubernetesResource resource : ServiceLoader.load(KubernetesResource.class)) {
            resourceTypes.add(resource.getClass());
        }

        assertThat(resourceTypes).contains(
                io.fabric8.kubernetes.api.model.node.v1alpha1.RuntimeClass.class,
                io.fabric8.kubernetes.api.model.node.v1alpha1.RuntimeClassList.class,
                RuntimeClass.class,
                RuntimeClassList.class,
                io.fabric8.kubernetes.api.model.node.v1beta1.RuntimeClass.class,
                io.fabric8.kubernetes.api.model.node.v1beta1.RuntimeClassList.class);
    }
}
