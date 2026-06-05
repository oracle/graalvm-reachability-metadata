/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Duration;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ListMetaBuilder;
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
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class Kubernetes_model_metricsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildsEditsAndSerializesPodMetrics() throws Exception {
        PodMetrics podMetrics = new PodMetricsBuilder()
                .withNewMetadata()
                    .withName("api-pod")
                    .withNamespace("production")
                    .addToLabels("app", "api")
                .endMetadata()
                .withTimestamp("2026-06-05T10:15:30Z")
                .withWindow(Duration.parse("30s"))
                .addNewContainer()
                    .withName("api")
                    .addToUsage("cpu", new Quantity("125m"))
                    .addToUsage("memory", new Quantity("256Mi"))
                .endContainer()
                .addNewContainer()
                    .withName("sidecar")
                    .addToUsage("cpu", new Quantity("25m"))
                    .addToUsage("memory", new Quantity("64Mi"))
                .endContainer()
                .addToAdditionalProperties("metrics.fabric8.io/source", Map.of("collector", "metrics-server"))
                .build();

        assertThat(podMetrics.getApiVersion()).isEqualTo("metrics.k8s.io/v1beta1");
        assertThat(podMetrics.getKind()).isEqualTo("PodMetrics");
        assertThat(podMetrics.getMetadata().getNamespace()).isEqualTo("production");
        assertThat(podMetrics.getWindow().getDuration()).isEqualTo(java.time.Duration.of(30, ChronoUnit.SECONDS));
        assertThat(podMetrics.getContainers()).extracting(ContainerMetrics::getName)
                .containsExactly("api", "sidecar");
        assertThat(podMetrics.getContainers().get(0).getUsage().get("cpu").getNumericalAmount())
                .isEqualByComparingTo(new BigDecimal("0.125"));

        PodMetrics edited = podMetrics.edit()
                .editMetadata()
                    .addToAnnotations("metrics.fabric8.io/sampled", "true")
                .endMetadata()
                .editMatchingContainer(container -> "api".equals(container.getName()))
                    .addToUsage("ephemeral-storage", new Quantity("1Gi"))
                .endContainer()
                .removeMatchingFromContainers(container -> "sidecar".equals(container.getName()))
                .addNewContainer()
                    .withName("worker")
                    .addToUsage("cpu", new Quantity("75m"))
                    .addToUsage("memory", new Quantity("128Mi"))
                .endContainer()
                .build();

        assertThat(podMetrics.getMetadata().getAnnotations()).isEmpty();
        assertThat(edited.getMetadata().getAnnotations()).containsEntry("metrics.fabric8.io/sampled", "true");
        assertThat(edited.getContainers()).extracting(ContainerMetrics::getName).containsExactly("api", "worker");
        assertThat(edited.getContainers().get(0).getUsage()).containsOnlyKeys("cpu", "memory", "ephemeral-storage");
        assertThat(edited.toBuilder().build()).isEqualTo(edited);
        assertThat(edited.toBuilder().build().hashCode()).isEqualTo(edited.hashCode());

        String json = MAPPER.writeValueAsString(edited);
        PodMetrics roundTripped = MAPPER.readValue(json, PodMetrics.class);

        assertThat(roundTripped).isEqualTo(edited);
        assertThat(roundTripped.getWindow().getDuration()).isEqualTo(java.time.Duration.of(30, ChronoUnit.SECONDS));
        assertThat(roundTripped.getAdditionalProperties()).containsKey("metrics.fabric8.io/source");
    }

    @Test
    void buildsAndSerializesPodMetricsLists() throws Exception {
        PodMetrics apiPod = new PodMetricsBuilder()
                .withNewMetadata()
                    .withName("api-pod")
                    .withNamespace("production")
                .endMetadata()
                .withTimestamp("2026-06-05T10:15:30Z")
                .withWindow(Duration.parse("15s"))
                .addNewContainer()
                    .withName("api")
                    .addToUsage("cpu", new Quantity("90m"))
                .endContainer()
                .build();

        PodMetricsList list = new PodMetricsListBuilder()
                .withMetadata(new ListMetaBuilder()
                        .withResourceVersion("rv-pods")
                        .withContinue("next-page")
                        .withRemainingItemCount(1L)
                        .build())
                .addNewItem()
                    .withNewMetadata()
                        .withName("worker-pod")
                        .withNamespace("production")
                    .endMetadata()
                    .withTimestamp("2026-06-05T10:15:45Z")
                    .withWindow(Duration.parse("15s"))
                    .addNewContainer()
                        .withName("worker")
                        .addToUsage("memory", new Quantity("192Mi"))
                    .endContainer()
                .endItem()
                .addToItems(apiPod)
                .addToAdditionalProperties("metrics.fabric8.io/page", 1)
                .build();

        assertThat(list.getApiVersion()).isEqualTo("metrics.k8s.io/v1beta1");
        assertThat(list.getKind()).isEqualTo("PodMetricsList");
        assertThat(list.getMetadata().getResourceVersion()).isEqualTo("rv-pods");
        assertThat(list.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("worker-pod", "api-pod");

        PodMetricsList edited = list.edit()
                .editFirstItem()
                    .editFirstContainer()
                        .addToUsage("cpu", new Quantity("55m"))
                    .endContainer()
                .endItem()
                .editMatchingItem(item -> "api-pod".equals(item.buildMetadata().getName()))
                    .withTimestamp("2026-06-05T10:16:00Z")
                .endItem()
                .build();

        String json = MAPPER.writeValueAsString(edited);
        PodMetricsList roundTripped = MAPPER.readValue(json, PodMetricsList.class);

        assertThat(roundTripped).isEqualTo(edited);
        assertThat(roundTripped.getItems()).extracting(PodMetrics::getTimestamp)
                .containsExactly("2026-06-05T10:15:45Z", "2026-06-05T10:16:00Z");
        assertThat(roundTripped.getItems().get(0).getContainers().get(0).getUsage()).containsKeys("memory", "cpu");
        assertThat(roundTripped.getAdditionalProperties()).containsEntry("metrics.fabric8.io/page", 1);
    }

    @Test
    void buildsEditsAndSerializesNodeMetrics() throws Exception {
        NodeMetrics nodeMetrics = new NodeMetricsBuilder()
                .withNewMetadata()
                    .withName("worker-1")
                    .addToLabels("node-role.kubernetes.io/worker", "true")
                .endMetadata()
                .withTimestamp("2026-06-05T10:17:00Z")
                .withWindow(Duration.parse("1m"))
                .addToUsage("cpu", new Quantity("1750m"))
                .addToUsage("memory", new Quantity("8192Mi"))
                .addToUsage("pods", new Quantity("18"))
                .addToAdditionalProperties("metrics.fabric8.io/zone", "test-zone")
                .build();

        assertThat(nodeMetrics.getApiVersion()).isEqualTo("metrics.k8s.io/v1beta1");
        assertThat(nodeMetrics.getKind()).isEqualTo("NodeMetrics");
        assertThat(nodeMetrics.getUsage().get("cpu").getNumericalAmount())
                .isEqualByComparingTo(new BigDecimal("1.750"));
        assertThat(nodeMetrics.getUsage().get("pods").toString()).isEqualTo("18");

        NodeMetrics edited = nodeMetrics.edit()
                .editMetadata()
                    .addToAnnotations("metrics.fabric8.io/capacity-source", "summary-api")
                .endMetadata()
                .removeFromUsage("pods")
                .addToUsage("ephemeral-storage", new Quantity("12Gi"))
                .withWindow(Duration.parse("45s"))
                .build();

        String json = MAPPER.writeValueAsString(edited);
        NodeMetrics roundTripped = MAPPER.readValue(json, NodeMetrics.class);

        assertThat(roundTripped).isEqualTo(edited);
        assertThat(roundTripped.getMetadata().getAnnotations())
                .containsEntry("metrics.fabric8.io/capacity-source", "summary-api");
        assertThat(roundTripped.getUsage()).containsOnlyKeys("cpu", "memory", "ephemeral-storage");
        assertThat(roundTripped.getWindow().getDuration()).isEqualTo(java.time.Duration.of(45, ChronoUnit.SECONDS));
        assertThat(roundTripped.getAdditionalProperties()).containsEntry("metrics.fabric8.io/zone", "test-zone");
    }

    @Test
    void buildsAndFiltersNodeMetricsLists() throws Exception {
        NodeMetrics workerOne = new NodeMetricsBuilder()
                .withNewMetadata()
                    .withName("worker-1")
                .endMetadata()
                .withTimestamp("2026-06-05T10:20:00Z")
                .withWindow(Duration.parse("30s"))
                .addToUsage("cpu", new Quantity("1000m"))
                .build();
        NodeMetrics workerTwo = new NodeMetricsBuilder()
                .withNewMetadata()
                    .withName("worker-2")
                .endMetadata()
                .withTimestamp("2026-06-05T10:20:00Z")
                .withWindow(Duration.parse("30s"))
                .addToUsage("cpu", new Quantity("500m"))
                .build();
        NodeMetrics controlPlane = new NodeMetricsBuilder()
                .withNewMetadata()
                    .withName("control-plane")
                .endMetadata()
                .withTimestamp("2026-06-05T10:20:00Z")
                .withWindow(Duration.parse("30s"))
                .addToUsage("cpu", new Quantity("250m"))
                .build();

        NodeMetricsListBuilder builder = new NodeMetricsListBuilder()
                .withMetadata(new ListMetaBuilder().withResourceVersion("rv-nodes").build())
                .withItems(workerOne, workerTwo, controlPlane);

        assertThat(builder.hasItems()).isTrue();
        assertThat(builder.hasMatchingItem(item -> "worker-2".equals(item.buildMetadata().getName()))).isTrue();
        assertThat(builder.buildMatchingItem(item -> item.getUsage().containsKey("cpu")).getMetadata().getName())
                .isEqualTo("worker-1");

        NodeMetricsList edited = builder
                .editMatchingItem(item -> "worker-2".equals(item.buildMetadata().getName()))
                    .addToUsage("memory", new Quantity("2048Mi"))
                .endItem()
                .removeMatchingFromItems(item -> "control-plane".equals(item.buildMetadata().getName()))
                .addNewItemLike(controlPlane)
                    .withTimestamp("2026-06-05T10:20:30Z")
                    .addToUsage("memory", new Quantity("4096Mi"))
                .endItem()
                .build();

        String json = MAPPER.writeValueAsString(edited);
        NodeMetricsList roundTripped = MAPPER.readValue(json, NodeMetricsList.class);

        assertThat(roundTripped.getApiVersion()).isEqualTo("metrics.k8s.io/v1beta1");
        assertThat(roundTripped.getKind()).isEqualTo("NodeMetricsList");
        assertThat(roundTripped.getMetadata().getResourceVersion()).isEqualTo("rv-nodes");
        assertThat(roundTripped.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("worker-1", "worker-2", "control-plane");
        assertThat(roundTripped.getItems().get(1).getUsage()).containsKeys("cpu", "memory");
        assertThat(roundTripped.getItems().get(2).getTimestamp()).isEqualTo("2026-06-05T10:20:30Z");
    }

    @Test
    void preservesStandaloneContainerMetricsExtensions() throws Exception {
        ContainerMetrics container = new ContainerMetricsBuilder()
                .withName("api")
                .addToUsage("cpu", new Quantity("250m"))
                .addToUsage("memory", new Quantity("384Mi"))
                .addToAdditionalProperties("metrics.fabric8.io/restartCount", 2)
                .build();

        assertThat(new ContainerMetricsBuilder(container).hasUsage()).isTrue();
        assertThat(new ContainerMetricsBuilder(container).getUsage()).containsKeys("cpu", "memory");

        ContainerMetrics edited = container.edit()
                .removeFromUsage("memory")
                .addToUsage("ephemeral-storage", new Quantity("2Gi"))
                .addToAdditionalProperties("metrics.fabric8.io/throttled", false)
                .build();

        String json = MAPPER.writeValueAsString(edited);
        ContainerMetrics roundTripped = MAPPER.readValue(json, ContainerMetrics.class);

        assertThat(roundTripped).isEqualTo(edited);
        assertThat(roundTripped.getUsage()).containsOnlyKeys("cpu", "ephemeral-storage");
        assertThat(roundTripped.getUsage().get("cpu").getNumericalAmount())
                .isEqualByComparingTo(new BigDecimal("0.250"));
        assertThat(roundTripped.getAdditionalProperties())
                .containsEntry("metrics.fabric8.io/restartCount", 2)
                .containsEntry("metrics.fabric8.io/throttled", false);
    }

    @Test
    void serviceLoaderDiscoversMetricsModelResources() {
        Set<Class<?>> resourceTypes = new LinkedHashSet<>();
        for (KubernetesResource resource : ServiceLoader.load(KubernetesResource.class)) {
            resourceTypes.add(resource.getClass());
        }

        assertThat(resourceTypes).contains(
                NodeMetrics.class,
                NodeMetricsList.class,
                PodMetrics.class,
                PodMetricsList.class);
    }
}
