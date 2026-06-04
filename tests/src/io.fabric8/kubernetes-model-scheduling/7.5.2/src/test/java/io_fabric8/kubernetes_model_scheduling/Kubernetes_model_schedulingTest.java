/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_scheduling;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ListMeta;
import io.fabric8.kubernetes.api.model.ListMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClass;
import io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClassBuilder;
import io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClassList;
import io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClassListBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class Kubernetes_model_schedulingTest {
    @Test
    void priorityClassBuilderCreatesEditableModelWithMetadataAndAdditionalProperties() {
        PriorityClass priorityClass = new PriorityClassBuilder()
                .withApiVersion("scheduling.k8s.io/v1")
                .withKind("PriorityClass")
                .withValue(1000)
                .withGlobalDefault(true)
                .withPreemptionPolicy("PreemptLowerPriority")
                .withDescription("Cluster critical application priority")
                .withNewMetadata()
                    .withName("critical-apps")
                    .addToLabels("tier", "control-plane")
                    .addToAnnotations("owner", "platform")
                .endMetadata()
                .addToAdditionalProperties("x-extension", Map.of("enabled", true))
                .build();

        assertThat(priorityClass).isInstanceOf(HasMetadata.class);
        assertThat(priorityClass.getApiVersion()).isEqualTo("scheduling.k8s.io/v1");
        assertThat(priorityClass.getKind()).isEqualTo("PriorityClass");
        assertThat(priorityClass.getValue()).isEqualTo(1000);
        assertThat(priorityClass.getGlobalDefault()).isTrue();
        assertThat(priorityClass.getPreemptionPolicy()).isEqualTo("PreemptLowerPriority");
        assertThat(priorityClass.getDescription()).isEqualTo("Cluster critical application priority");
        assertThat(priorityClass.getMetadata().getName()).isEqualTo("critical-apps");
        assertThat(priorityClass.getMetadata().getLabels()).containsEntry("tier", "control-plane");
        assertThat(priorityClass.getMetadata().getAnnotations()).containsEntry("owner", "platform");
        assertThat(priorityClass.getAdditionalProperties()).containsEntry("x-extension", Map.of("enabled", true));

        PriorityClass edited = priorityClass.edit()
                .withDescription("Edited description")
                .withGlobalDefault(false)
                .editMetadata()
                    .addToLabels("environment", "prod")
                .endMetadata()
                .removeFromAdditionalProperties("x-extension")
                .build();

        assertThat(edited.getDescription()).isEqualTo("Edited description");
        assertThat(edited.getGlobalDefault()).isFalse();
        assertThat(edited.getMetadata().getLabels()).containsEntry("environment", "prod");
        assertThat(edited.getAdditionalProperties()).doesNotContainKey("x-extension");
        assertThat(priorityClass.getMetadata().getLabels()).doesNotContainKey("environment");
    }

    @Test
    void priorityClassConstructorsAccessorsEqualityAndBuilderCopiesRemainConsistent() {
        ObjectMeta metadata = new ObjectMetaBuilder()
                .withName("batch-jobs")
                .withNamespace("scheduling")
                .addToLabels("workload", "batch")
                .build();
        PriorityClass constructed = new PriorityClass(
                "scheduling.k8s.io/v1",
                "Non critical batch jobs",
                false,
                "PriorityClass",
                metadata,
                "Never",
                10);
        constructed.setAdditionalProperty("ranking", 2);

        PriorityClass copied = new PriorityClassBuilder(constructed).build();
        PriorityClass rebuilt = constructed.toBuilder().withValue(20).build();

        assertThat(copied).isEqualTo(constructed);
        assertThat(copied.hashCode()).isEqualTo(constructed.hashCode());
        assertThat(copied.toString()).contains("batch-jobs", "Non critical batch jobs");
        assertThat(copied.getAdditionalProperties()).containsEntry("ranking", 2);
        assertThat(rebuilt.getValue()).isEqualTo(20);
        assertThat(rebuilt).isNotEqualTo(constructed);
    }

    @Test
    void priorityClassListBuilderSupportsNestedItemsMetadataMatchingAndRemoval() {
        PriorityClass low = new PriorityClassBuilder()
                .withApiVersion("scheduling.k8s.io/v1")
                .withKind("PriorityClass")
                .withValue(1)
                .withDescription("Low priority")
                .withNewMetadata().withName("low").endMetadata()
                .build();
        PriorityClass high = new PriorityClassBuilder()
                .withApiVersion("scheduling.k8s.io/v1")
                .withKind("PriorityClass")
                .withValue(100)
                .withDescription("High priority")
                .withNewMetadata().withName("high").endMetadata()
                .build();
        ListMeta metadata = new ListMetaBuilder()
                .withResourceVersion("42")
                .withContinue("next-page")
                .withRemainingItemCount(1L)
                .build();

        PriorityClassList priorityClasses = new PriorityClassListBuilder()
                .withApiVersion("scheduling.k8s.io/v1")
                .withKind("PriorityClassList")
                .withMetadata(metadata)
                .addToItems(low)
                .addNewItemLike(high)
                    .editMetadata()
                        .addToAnnotations("audited", "true")
                    .endMetadata()
                .endItem()
                .addNewItem()
                    .withApiVersion("scheduling.k8s.io/v1")
                    .withKind("PriorityClass")
                    .withValue(50)
                    .withDescription("Medium priority")
                    .withNewMetadata().withName("medium").endMetadata()
                .endItem()
                .addToAdditionalProperties("source", "unit-test")
                .build();

        assertThat(priorityClasses.getApiVersion()).isEqualTo("scheduling.k8s.io/v1");
        assertThat(priorityClasses.getKind()).isEqualTo("PriorityClassList");
        assertThat(priorityClasses.getMetadata().getResourceVersion()).isEqualTo("42");
        assertThat(priorityClasses.getMetadata().getContinue()).isEqualTo("next-page");
        assertThat(priorityClasses.getMetadata().getRemainingItemCount()).isEqualTo(1L);
        assertThat(priorityClasses.getAdditionalProperties()).containsEntry("source", "unit-test");
        assertThat(priorityClasses.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("low", "high", "medium");
        assertThat(priorityClasses.getItems().get(1).getMetadata().getAnnotations()).containsEntry("audited", "true");

        PriorityClassList updated = priorityClasses.toBuilder()
                .editMatchingItem(item -> item.buildMetadata().getName().equals("medium"))
                    .withValue(55)
                    .withDescription("Adjusted medium priority")
                .endItem()
                .removeMatchingFromItems(item -> item.getValue().equals(1))
                .build();

        assertThat(updated.getItems()).hasSize(2);
        assertThat(updated.getItems()).extracting(PriorityClass::getValue).containsExactly(100, 55);
        assertThat(updated.getItems()).extracting(PriorityClass::getDescription)
                .contains("Adjusted medium priority");
    }

    @Test
    void priorityClassListConstructorsAndSettersPreserveItemsAndExtensionProperties() {
        PriorityClass item = new PriorityClassBuilder()
                .withApiVersion("scheduling.k8s.io/v1")
                .withKind("PriorityClass")
                .withValue(5)
                .withNewMetadata().withName("interactive").endMetadata()
                .build();
        PriorityClassList constructed = new PriorityClassList(
                "scheduling.k8s.io/v1",
                List.of(item),
                "PriorityClassList",
                new ListMetaBuilder().withResourceVersion("77").build());
        constructed.setAdditionalProperties(new HashMap<>(Map.of("cluster", "test")));

        PriorityClass replacement = item.toBuilder()
                .withValue(6)
                .editMetadata().withName("interactive-updated").endMetadata()
                .build();
        constructed.setItems(List.of(replacement));
        constructed.setAdditionalProperty("validated", true);

        PriorityClassList copied = constructed.edit().build();

        assertThat(copied).isEqualTo(constructed);
        assertThat(copied.hashCode()).isEqualTo(constructed.hashCode());
        assertThat(copied.toString()).contains("interactive-updated", "PriorityClassList");
        assertThat(copied.getItems()).singleElement().satisfies(priorityClass -> {
            assertThat(priorityClass.getMetadata().getName()).isEqualTo("interactive-updated");
            assertThat(priorityClass.getValue()).isEqualTo(6);
        });
        assertThat(copied.getAdditionalProperties()).containsEntry("cluster", "test")
                .containsEntry("validated", true);
    }

    @Test
    void v1beta1PriorityClassModelsSupportTheSameBuilderPatterns() {
        io.fabric8.kubernetes.api.model.scheduling.v1beta1.PriorityClass priorityClass =
                new io.fabric8.kubernetes.api.model.scheduling.v1beta1.PriorityClassBuilder()
                        .withApiVersion("scheduling.k8s.io/v1beta1")
                        .withKind("PriorityClass")
                        .withValue(900)
                        .withGlobalDefault(false)
                        .withPreemptionPolicy("PreemptLowerPriority")
                        .withDescription("v1beta1 priority")
                        .withNewMetadata()
                            .withName("beta-priority")
                            .addToLabels("api", "beta")
                        .endMetadata()
                        .addToAdditionalProperties("beta", true)
                        .build();
        io.fabric8.kubernetes.api.model.scheduling.v1beta1.PriorityClassList list =
                new io.fabric8.kubernetes.api.model.scheduling.v1beta1.PriorityClassListBuilder()
                        .withApiVersion("scheduling.k8s.io/v1beta1")
                        .withKind("PriorityClassList")
                        .withNewMetadata("continue-token", 1L, "100", null)
                        .addToItems(priorityClass)
                        .build();

        assertThat(priorityClass.toBuilder().withValue(901).build().getValue()).isEqualTo(901);
        assertThat(priorityClass.getMetadata().getLabels()).containsEntry("api", "beta");
        assertThat(priorityClass.getAdditionalProperties()).containsEntry("beta", true);
        assertThat(list.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getApiVersion()).isEqualTo("scheduling.k8s.io/v1beta1");
            assertThat(item.getMetadata().getName()).isEqualTo("beta-priority");
        });
        assertThat(list.edit().editFirstItem().withDescription("edited beta priority").endItem().build()
                .getItems().get(0).getDescription()).isEqualTo("edited beta priority");
    }

    @Test
    void v1alpha1PriorityClassModelsSupportConstructorsSettersAndLists() {
        ObjectMeta metadata = new ObjectMetaBuilder()
                .withName("alpha-priority")
                .addToAnnotations("release", "alpha")
                .build();
        io.fabric8.kubernetes.api.model.scheduling.v1alpha1.PriorityClass priorityClass =
                new io.fabric8.kubernetes.api.model.scheduling.v1alpha1.PriorityClass(
                        "scheduling.k8s.io/v1alpha1",
                        "v1alpha1 priority",
                        false,
                        "PriorityClass",
                        metadata,
                        "Never",
                        700);
        priorityClass.setAdditionalProperty("alpha", "enabled");

        io.fabric8.kubernetes.api.model.scheduling.v1alpha1.PriorityClass edited = priorityClass.edit()
                .withGlobalDefault(true)
                .editMetadata()
                    .addToLabels("api", "alpha")
                .endMetadata()
                .build();
        io.fabric8.kubernetes.api.model.scheduling.v1alpha1.PriorityClassList list =
                new io.fabric8.kubernetes.api.model.scheduling.v1alpha1.PriorityClassListBuilder()
                        .withApiVersion("scheduling.k8s.io/v1alpha1")
                        .withKind("PriorityClassList")
                        .addAllToItems(List.of(priorityClass, edited))
                        .removeMatchingFromItems(item -> item.getGlobalDefault())
                        .build();

        assertThat(priorityClass.getAdditionalProperties()).containsEntry("alpha", "enabled");
        assertThat(edited.getGlobalDefault()).isTrue();
        assertThat(edited.getMetadata().getLabels()).containsEntry("api", "alpha");
        assertThat(list.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMetadata().getName()).isEqualTo("alpha-priority");
            assertThat(item.getGlobalDefault()).isFalse();
        });
    }
}
