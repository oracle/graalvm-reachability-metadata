/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_metrics;

import java.util.Map;

import io.fabric8.kubernetes.api.model.Duration;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetricsBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetricsBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetricsList;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetricsListBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsList;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsListBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Kubernetes_model_metricsTest {
    @Test
    void podMetricsBuildsContainerUsageMetadataAndMetricWindow() {
        PodMetrics podMetrics = new PodMetricsBuilder()
                .withApiVersion("metrics.k8s.io/v1beta1")
                .withKind("PodMetrics")
                .withNewMetadata()
                    .withName("checkout-0")
                    .withNamespace("store")
                    .addToLabels("app", "checkout")
                .endMetadata()
                .withTimestamp("2026-07-02T12:00:00Z")
                .addNewContainer()
                    .withName("application")
                    .addToUsage("cpu", new Quantity("125", "m"))
                    .addToUsage("memory", new Quantity("256", "Mi"))
                    .addToAdditionalProperties("source", "metrics-server")
                .endContainer()
                .addNewContainer()
                    .withName("sidecar")
                    .addToUsage("cpu", new Quantity("25", "m"))
                .endContainer()
                .addToAdditionalProperties("sample", "pod")
                .build();

        assertThat(podMetrics).isInstanceOf(Namespaced.class);
        assertThat(podMetrics.getApiVersion()).isEqualTo("metrics.k8s.io/v1beta1");
        assertThat(podMetrics.getKind()).isEqualTo("PodMetrics");
        assertThat(podMetrics.getMetadata().getNamespace()).isEqualTo("store");
        assertThat(podMetrics.getMetadata().getLabels()).containsEntry("app", "checkout");
        assertThat(podMetrics.getTimestamp()).isEqualTo("2026-07-02T12:00:00Z");
        assertThat(podMetrics.getContainers()).extracting(ContainerMetrics::getName)
                .containsExactly("application", "sidecar");
        assertThat(podMetrics.getContainers().get(0).getUsage().get("cpu"))
                .extracting(Quantity::getAmount, Quantity::getFormat)
                .containsExactly("125", "m");
        assertThat(podMetrics.getContainers().get(0).getAdditionalProperties())
                .containsEntry("source", "metrics-server");
        assertThat(podMetrics.getAdditionalProperties()).containsEntry("sample", "pod");
    }

    @Test
    void podMetricsSupportMetricCollectionWindows() {
        PodMetrics podMetrics = new PodMetricsBuilder()
                .withNewMetadata()
                    .withName("inventory-0")
                .endMetadata()
                .withTimestamp("2026-07-02T12:03:00Z")
                .withWindow(new Duration(java.time.Duration.ofSeconds(30)))
                .build();

        assertThat(podMetrics.getWindow().getDuration()).isEqualTo(java.time.Duration.ofSeconds(30));
    }

    @Test
    void podMetricBuildersSupportCopyingNestedEditsAndContainerSelection() {
        ContainerMetrics application = new ContainerMetricsBuilder()
                .withName("application")
                .withUsage(Map.of("cpu", new Quantity("250", "m")))
                .build();
        ContainerMetrics sidecar = new ContainerMetrics("sidecar", Map.of("memory", new Quantity("64", "Mi")));
        sidecar.setAdditionalProperty("injected", true);

        PodMetrics original = new PodMetricsBuilder()
                .withNewMetadata()
                    .withName("payments-0")
                .endMetadata()
                .withContainers(application, sidecar)
                .build();
        PodMetrics updated = original.toBuilder()
                .editMatchingContainer(container -> "application".equals(container.getName()))
                    .addToUsage("memory", new Quantity("512", "Mi"))
                .endContainer()
                .editLastContainer()
                    .withName("telemetry")
                .endContainer()
                .addNewContainerLike(application)
                    .withName("worker")
                .endContainer()
                .removeMatchingFromContainers(container -> "telemetry".equals(container.getName()))
                .build();

        assertThat(original.getContainers()).extracting(ContainerMetrics::getName)
                .containsExactly("application", "sidecar");
        assertThat(original.getContainers().get(1).getAdditionalProperties()).containsEntry("injected", true);
        assertThat(updated.getContainers()).extracting(ContainerMetrics::getName)
                .containsExactly("application", "worker");
        assertThat(updated.getContainers().get(0).getUsage().get("memory"))
                .extracting(Quantity::getAmount, Quantity::getFormat)
                .containsExactly("512", "Mi");
        assertThat(updated.getContainers().get(1).getUsage().get("cpu"))
                .extracting(Quantity::getAmount, Quantity::getFormat)
                .containsExactly("250", "m");
    }

    @Test
    void nodeMetricsAndListsPreserveUsageExtensionsAndEditableItems() {
        NodeMetrics node = new NodeMetricsBuilder()
                .withApiVersion("metrics.k8s.io/v1beta1")
                .withKind("NodeMetrics")
                .withNewMetadata()
                    .withName("worker-a")
                    .addToLabels("node-role.kubernetes.io/worker", "")
                .endMetadata()
                .withTimestamp("2026-07-02T12:01:00Z")
                .addToUsage("cpu", new Quantity("1800", "m"))
                .addToUsage("memory", new Quantity("4", "Gi"))
                .addToAdditionalProperties("provider", "test-cluster")
                .build();
        NodeMetrics revised = node.edit()
                .editMetadata()
                    .addToAnnotations("metrics.example.com/revised", "true")
                .endMetadata()
                .removeFromUsage("cpu")
                .addToUsage("pods", new Quantity("12", ""))
                .removeFromAdditionalProperties("provider")
                .build();

        assertThat(node.getUsage()).containsKeys("cpu", "memory");
        assertThat(node.getAdditionalProperties()).containsEntry("provider", "test-cluster");
        assertThat(revised.getMetadata().getAnnotations()).containsEntry("metrics.example.com/revised", "true");
        assertThat(revised.getUsage()).doesNotContainKey("cpu").containsKey("pods");
        assertThat(revised.getUsage().get("pods").getAmount()).isEqualTo("12");
        assertThat(revised.getAdditionalProperties()).doesNotContainKey("provider");

        NodeMetricsList nodes = new NodeMetricsListBuilder()
                .withApiVersion("metrics.k8s.io/v1beta1")
                .withKind("NodeMetricsList")
                .withNewMetadata("node-token", 2L, "10", null)
                .addToItems(node, revised)
                .addToAdditionalProperties("source", "metrics-api")
                .build();
        NodeMetricsList filteredNodes = nodes.toBuilder()
                .editMatchingItem(item -> item.getUsage().containsKey("pods"))
                    .withTimestamp("2026-07-02T12:02:00Z")
                .endItem()
                .removeMatchingFromItems(item -> item.getUsage().containsKey("cpu"))
                .build();

        KubernetesResourceList<NodeMetrics> resourceList = nodes;
        assertThat(resourceList.getItems()).hasSize(2);
        assertThat(nodes.getMetadata().getContinue()).isEqualTo("node-token");
        assertThat(nodes.getAdditionalProperties()).containsEntry("source", "metrics-api");
        assertThat(filteredNodes.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getTimestamp()).isEqualTo("2026-07-02T12:02:00Z");
            assertThat(item.getUsage()).containsKey("pods");
        });
    }

    @Test
    void podMetricsListBuildersSupportIndexedInsertionAndItemQueries() {
        PodMetrics first = new PodMetricsBuilder()
                .withNewMetadata()
                    .withName("orders-0")
                .endMetadata()
                .build();
        PodMetrics second = new PodMetricsBuilder()
                .withNewMetadata()
                    .withName("orders-2")
                .endMetadata()
                .build();
        PodMetrics canary = new PodMetricsBuilder()
                .withNewMetadata()
                    .withName("orders-1")
                .endMetadata()
                .build();

        PodMetricsListBuilder builder = new PodMetricsListBuilder()
                .withItems(first, second)
                .addToItems(1, canary);

        assertThat(builder.hasMatchingItem(item -> "orders-1".equals(item.buildMetadata().getName())))
                .isTrue();
        assertThat(builder.buildItem(1).getMetadata().getName()).isEqualTo("orders-1");
        assertThat(builder.buildMatchingItem(item -> "orders-2".equals(item.buildMetadata().getName())))
                .isEqualTo(second);
        assertThat(builder.buildItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("orders-0", "orders-1", "orders-2");

        PodMetricsList updated = builder.editItem(1)
                .editMetadata()
                    .withName("orders-canary")
                .endMetadata()
                .endItem()
                .build();

        assertThat(updated.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("orders-0", "orders-canary", "orders-2");
    }

    @Test
    void podMetricListsSupportItemCopiesPositionalReplacementAndExtensions() {
        PodMetrics first = new PodMetricsBuilder()
                .withNewMetadata()
                    .withName("catalog-0")
                    .withNamespace("store")
                .endMetadata()
                .addNewContainer()
                    .withName("catalog")
                    .addToUsage("cpu", new Quantity("50", "m"))
                .endContainer()
                .build();
        PodMetricsList list = new PodMetricsListBuilder()
                .withApiVersion("metrics.k8s.io/v1beta1")
                .withKind("PodMetricsList")
                .withNewMetadata("pod-token", 1L, "20", null)
                .addToItems(first)
                .addNewItemLike(first)
                    .editMetadata()
                        .withName("catalog-1")
                    .endMetadata()
                    .editFirstContainer()
                        .addToUsage("memory", new Quantity("128", "Mi"))
                    .endContainer()
                .endItem()
                .addToAdditionalProperties("partial", false)
                .build();
        PodMetricsList replaced = list.edit()
                .setNewItemLike(0, first)
                    .editMetadata()
                        .withName("catalog-replacement")
                    .endMetadata()
                .endItem()
                .removeFromAdditionalProperties("partial")
                .build();

        assertThat(list.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("catalog-0", "catalog-1");
        assertThat(list.getItems().get(1).getContainers().get(0).getUsage().get("memory"))
                .extracting(Quantity::getAmount, Quantity::getFormat)
                .containsExactly("128", "Mi");
        assertThat(list.getAdditionalProperties()).containsEntry("partial", false);
        assertThat(replaced.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("catalog-replacement", "catalog-1");
        assertThat(replaced.getAdditionalProperties()).doesNotContainKey("partial");
    }
}
