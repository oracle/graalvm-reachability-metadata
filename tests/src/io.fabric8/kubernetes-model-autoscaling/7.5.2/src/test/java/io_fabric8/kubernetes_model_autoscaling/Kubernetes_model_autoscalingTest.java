/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_autoscaling;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.autoscaling.v1.Scale;
import io.fabric8.kubernetes.api.model.autoscaling.v1.ScaleBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerList;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerListBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpec;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpecBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricStatus;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricStatusBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Kubernetes_model_autoscalingTest {
    @Test
    void v1ScaleAndHorizontalPodAutoscalerBuildersSupportSpecsStatusesAndCopying() {
        Scale scale = new ScaleBuilder()
                .withApiVersion("autoscaling/v1")
                .withKind("Scale")
                .withNewMetadata()
                    .withName("web-scale")
                    .withNamespace("prod")
                    .addToLabels("app", "web")
                .endMetadata()
                .withNewSpec()
                    .withReplicas(3)
                    .addToAdditionalProperties("requested-by", "test")
                .endSpec()
                .withNewStatus()
                    .withReplicas(2)
                    .withSelector("app=web")
                .endStatus()
                .build();

        assertThat(scale.getMetadata().getLabels()).containsEntry("app", "web");
        assertThat(scale.getSpec().getReplicas()).isEqualTo(3);
        assertThat(scale.getSpec().getAdditionalProperties()).containsEntry("requested-by", "test");
        assertThat(scale.getStatus().getSelector()).isEqualTo("app=web");

        Scale editedScale = new ScaleBuilder(scale)
                .editSpec()
                    .withReplicas(5)
                .endSpec()
                .editStatus()
                    .withReplicas(4)
                .endStatus()
                .build();

        assertThat(editedScale.getSpec().getReplicas()).isEqualTo(5);
        assertThat(editedScale.getStatus().getReplicas()).isEqualTo(4);
        assertThat(editedScale.getMetadata().getName()).isEqualTo("web-scale");

        io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler autoscaler =
                new io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscalerBuilder()
                        .withApiVersion("autoscaling/v1")
                        .withKind("HorizontalPodAutoscaler")
                        .withNewMetadata()
                            .withName("web-hpa-v1")
                            .withNamespace("prod")
                        .endMetadata()
                        .withNewSpec()
                            .withNewScaleTargetRef("apps/v1", "Deployment", "web")
                            .withMinReplicas(2)
                            .withMaxReplicas(8)
                            .withTargetCPUUtilizationPercentage(70)
                        .endSpec()
                        .withNewStatus()
                            .withCurrentReplicas(3)
                            .withDesiredReplicas(4)
                            .withCurrentCPUUtilizationPercentage(63)
                            .withObservedGeneration(10L)
                        .endStatus()
                        .addToAdditionalProperties("x-kubernetes-note", "kept")
                        .build();

        assertThat(autoscaler.getSpec().getScaleTargetRef().getName()).isEqualTo("web");
        assertThat(autoscaler.getSpec().getTargetCPUUtilizationPercentage()).isEqualTo(70);
        assertThat(autoscaler.getStatus().getDesiredReplicas()).isEqualTo(4);
        assertThat(autoscaler.getAdditionalProperties()).containsEntry("x-kubernetes-note", "kept");
    }

    @Test
    void v2HorizontalPodAutoscalerSupportsMetricsBehaviorStatusListsAndEdits() {
        MetricSpec resourceMetric = new MetricSpecBuilder()
                .withType("Resource")
                .withNewResource()
                    .withName("cpu")
                    .withNewTarget()
                        .withType("Utilization")
                        .withAverageUtilization(60)
                    .endTarget()
                .endResource()
                .build();
        MetricSpec podsMetric = new MetricSpecBuilder()
                .withType("Pods")
                .withNewPods()
                    .withNewMetric()
                        .withName("requests-per-second")
                        .withNewSelector()
                            .addToMatchLabels("tier", "frontend")
                        .endSelector()
                    .endMetric()
                    .withNewTarget()
                        .withType("AverageValue")
                        .withNewAverageValue("100")
                    .endTarget()
                .endPods()
                .build();
        MetricSpec externalMetric = new MetricSpecBuilder()
                .withType("External")
                .withNewExternal()
                    .withNewMetric()
                        .withName("queue_messages_ready")
                        .withNewSelector()
                            .addToMatchLabels("queue", "checkout")
                        .endSelector()
                    .endMetric()
                    .withNewTarget()
                        .withType("Value")
                        .withNewValue("30")
                    .endTarget()
                .endExternal()
                .build();
        MetricSpec objectMetric = new MetricSpecBuilder()
                .withType("Object")
                .withNewObject()
                    .withNewDescribedObject("networking.k8s.io/v1", "Ingress", "checkout")
                    .withNewMetric()
                        .withName("hits-per-second")
                    .endMetric()
                    .withNewTarget()
                        .withType("Value")
                        .withNewValue("500")
                    .endTarget()
                .endObject()
                .build();

        HorizontalPodAutoscaler autoscaler = new HorizontalPodAutoscalerBuilder()
                .withApiVersion("autoscaling/v2")
                .withKind("HorizontalPodAutoscaler")
                .withNewMetadata()
                    .withName("checkout-hpa")
                    .withNamespace("shop")
                .endMetadata()
                .withNewSpec()
                    .withNewScaleTargetRef("apps/v1", "Deployment", "checkout")
                    .withMinReplicas(2)
                    .withMaxReplicas(12)
                    .withMetrics(resourceMetric, podsMetric, externalMetric, objectMetric)
                    .withNewBehavior()
                        .withNewScaleUp()
                            .withSelectPolicy("Max")
                            .withStabilizationWindowSeconds(0)
                            .addNewPolicy()
                                .withType("Percent")
                                .withValue(100)
                                .withPeriodSeconds(15)
                            .endPolicy()
                        .endScaleUp()
                        .withNewScaleDown()
                            .withSelectPolicy("Min")
                            .withStabilizationWindowSeconds(300)
                            .withNewTolerance("0.10")
                            .addNewPolicy()
                                .withType("Pods")
                                .withValue(2)
                                .withPeriodSeconds(60)
                            .endPolicy()
                        .endScaleDown()
                    .endBehavior()
                .endSpec()
                .withNewStatus()
                    .withObservedGeneration(7L)
                    .withCurrentReplicas(4)
                    .withDesiredReplicas(6)
                    .addNewCondition()
                        .withType("AbleToScale")
                        .withStatus("True")
                        .withReason("ReadyForNewScale")
                        .withMessage("recommended size matches current size")
                    .endCondition()
                    .addNewCurrentMetric()
                        .withType("Resource")
                        .withNewResource()
                            .withName("cpu")
                            .withNewCurrent()
                                .withAverageUtilization(58)
                                .withNewAverageValue("580m")
                            .endCurrent()
                        .endResource()
                    .endCurrentMetric()
                .endStatus()
                .build();

        assertThat(autoscaler.getSpec().getMetrics()).hasSize(4);
        assertThat(autoscaler.getSpec().getBehavior().getScaleUp().getPolicies()).hasSize(1);
        assertThat(autoscaler.getSpec().getBehavior().getScaleDown().getTolerance().getAmount()).isEqualTo("0.10");
        assertThat(autoscaler.getStatus().getConditions().get(0).getReason()).isEqualTo("ReadyForNewScale");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(0).getResource().getCurrent().getAverageUtilization())
                .isEqualTo(58);

        HorizontalPodAutoscaler editedAutoscaler = new HorizontalPodAutoscalerBuilder(autoscaler)
                .editSpec()
                    .editMatchingMetric(metric -> "External".equals(metric.getType()))
                        .editExternal()
                            .editMetric()
                                .withName("queue_depth")
                            .endMetric()
                        .endExternal()
                    .endMetric()
                    .addToAdditionalProperties("spec-extension", Boolean.TRUE)
                .endSpec()
                .build();

        assertThat(editedAutoscaler.getSpec().getMetrics().stream()
                .filter(metric -> "External".equals(metric.getType()))
                .findFirst()
                .orElseThrow()
                .getExternal().getMetric().getName()).isEqualTo("queue_depth");
        assertThat(editedAutoscaler.getSpec().getAdditionalProperties()).containsEntry("spec-extension", Boolean.TRUE);

        HorizontalPodAutoscalerList list = new HorizontalPodAutoscalerListBuilder()
                .withApiVersion("autoscaling/v2")
                .withKind("HorizontalPodAutoscalerList")
                .addToItems(autoscaler, editedAutoscaler)
                .build();

        assertThat(list.getItems())
                .extracting(item -> item.getMetadata().getName())
                .containsExactly("checkout-hpa", "checkout-hpa");
    }

    @Test
    void v2MetricIdentifiersSupportSelectorMatchExpressions() {
        MetricSpec externalMetric = new MetricSpecBuilder()
                .withType("External")
                .withNewExternal()
                    .withNewMetric()
                        .withName("queue_latency_seconds")
                        .withNewSelector()
                            .addNewMatchExpression()
                                .withKey("queue")
                                .withOperator("In")
                                .withValues("checkout", "payments")
                            .endMatchExpression()
                            .addNewMatchExpression()
                                .withKey("environment")
                                .withOperator("NotIn")
                                .withValues("dev")
                            .endMatchExpression()
                        .endSelector()
                    .endMetric()
                    .withNewTarget()
                        .withType("AverageValue")
                        .withNewAverageValue("250m")
                    .endTarget()
                .endExternal()
                .build();
        MetricSpec podsMetric = new MetricSpecBuilder()
                .withType("Pods")
                .withNewPods()
                    .withNewMetric()
                        .withName("open-connections")
                        .withNewSelector()
                            .addNewMatchExpression()
                                .withKey("tier")
                                .withOperator("Exists")
                            .endMatchExpression()
                        .endSelector()
                    .endMetric()
                    .withNewTarget()
                        .withType("AverageValue")
                        .withNewAverageValue("20")
                    .endTarget()
                .endPods()
                .build();

        HorizontalPodAutoscaler autoscaler = new HorizontalPodAutoscalerBuilder()
                .withApiVersion("autoscaling/v2")
                .withKind("HorizontalPodAutoscaler")
                .withNewMetadata()
                    .withName("selector-hpa")
                .endMetadata()
                .withNewSpec()
                    .withNewScaleTargetRef("apps/v1", "Deployment", "selector-app")
                    .withMinReplicas(1)
                    .withMaxReplicas(6)
                    .withMetrics(externalMetric, podsMetric)
                .endSpec()
                .build();

        MetricSpec selectedExternalMetric = autoscaler.getSpec().getMetrics().get(0);
        assertThat(selectedExternalMetric.getExternal().getMetric().getSelector().getMatchExpressions())
                .extracting(requirement -> requirement.getKey())
                .containsExactly("queue", "environment");
        assertThat(selectedExternalMetric.getExternal().getMetric().getSelector().getMatchExpressions().get(0)
                .getValues()).containsExactly("checkout", "payments");
        assertThat(selectedExternalMetric.getExternal().getMetric().getSelector().getMatchExpressions().get(1)
                .getOperator()).isEqualTo("NotIn");
        assertThat(selectedExternalMetric.getExternal().getTarget().getAverageValue().getAmount()).isEqualTo("250");
        assertThat(selectedExternalMetric.getExternal().getTarget().getAverageValue().getFormat()).isEqualTo("m");

        MetricSpec selectedPodsMetric = autoscaler.getSpec().getMetrics().get(1);
        assertThat(selectedPodsMetric.getPods().getMetric().getSelector().getMatchExpressions()).singleElement()
                .satisfies(requirement -> {
                    assertThat(requirement.getKey()).isEqualTo("tier");
                    assertThat(requirement.getOperator()).isEqualTo("Exists");
                });
    }

    @Test
    void v2HorizontalPodAutoscalerStatusSupportsAllCurrentMetricKinds() {
        MetricStatus objectStatus = new MetricStatusBuilder()
                .withType("Object")
                .withNewObject()
                    .withNewDescribedObject("batch/v1", "Job", "daily-import")
                    .withNewMetric()
                        .withName("processed-records")
                        .withNewSelector()
                            .addToMatchLabels("pipeline", "daily")
                        .endSelector()
                    .endMetric()
                    .withNewCurrent()
                        .withNewValue("42")
                    .endCurrent()
                .endObject()
                .build();
        MetricStatus podsStatus = new MetricStatusBuilder()
                .withType("Pods")
                .withNewPods()
                    .withNewMetric()
                        .withName("requests-per-second")
                    .endMetric()
                    .withNewCurrent()
                        .withNewAverageValue("125")
                    .endCurrent()
                .endPods()
                .build();
        MetricStatus externalStatus = new MetricStatusBuilder()
                .withType("External")
                .withNewExternal()
                    .withNewMetric()
                        .withName("queue_messages_ready")
                        .withNewSelector()
                            .addToMatchLabels("queue", "payments")
                        .endSelector()
                    .endMetric()
                    .withNewCurrent()
                        .withNewAverageValue("8")
                    .endCurrent()
                .endExternal()
                .build();
        MetricStatus containerStatus = new MetricStatusBuilder()
                .withType("ContainerResource")
                .withNewContainerResource()
                    .withName("memory")
                    .withContainer("worker")
                    .withNewCurrent()
                        .withAverageUtilization(71)
                        .withNewAverageValue("512Mi")
                    .endCurrent()
                .endContainerResource()
                .build();

        HorizontalPodAutoscaler autoscaler = new HorizontalPodAutoscalerBuilder()
                .withApiVersion("autoscaling/v2")
                .withKind("HorizontalPodAutoscaler")
                .withNewMetadata()
                    .withName("worker-hpa")
                .endMetadata()
                .withNewSpec()
                    .withNewScaleTargetRef("apps/v1", "Deployment", "worker")
                    .withMinReplicas(1)
                    .withMaxReplicas(10)
                .endSpec()
                .withNewStatus()
                    .withLastScaleTime("2024-01-01T00:00:00Z")
                    .withCurrentMetrics(objectStatus, podsStatus, externalStatus, containerStatus)
                .endStatus()
                .build();

        assertThat(autoscaler.getStatus().getLastScaleTime()).isEqualTo("2024-01-01T00:00:00Z");
        assertThat(autoscaler.getStatus().getCurrentMetrics())
                .extracting(MetricStatus::getType)
                .containsExactly("Object", "Pods", "External", "ContainerResource");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(0).getObject().getDescribedObject().getKind())
                .isEqualTo("Job");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(0).getObject().getMetric().getSelector()
                .getMatchLabels()).containsEntry("pipeline", "daily");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(0).getObject().getCurrent().getValue()
                .getAmount()).isEqualTo("42");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(1).getPods().getCurrent().getAverageValue()
                .getAmount()).isEqualTo("125");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(2).getExternal().getMetric().getSelector()
                .getMatchLabels()).containsEntry("queue", "payments");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(2).getExternal().getCurrent().getAverageValue()
                .getAmount()).isEqualTo("8");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(3).getContainerResource().getContainer())
                .isEqualTo("worker");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(3).getContainerResource().getCurrent()
                .getAverageUtilization()).isEqualTo(71);
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(3).getContainerResource().getCurrent()
                .getAverageValue().getFormat()).isEqualTo("Mi");
    }

    @Test
    void v2beta1BuildersSupportLegacyMetricShapes() {
        io.fabric8.kubernetes.api.model.autoscaling.v2beta1.HorizontalPodAutoscaler autoscaler =
                new io.fabric8.kubernetes.api.model.autoscaling.v2beta1.HorizontalPodAutoscalerBuilder()
                        .withApiVersion("autoscaling/v2beta1")
                        .withKind("HorizontalPodAutoscaler")
                        .withNewMetadata()
                            .withName("legacy-hpa")
                        .endMetadata()
                        .withNewSpec()
                            .withNewScaleTargetRef("apps/v1", "StatefulSet", "worker")
                            .withMinReplicas(1)
                            .withMaxReplicas(5)
                            .addNewMetric()
                                .withType("Resource")
                                .withNewResource()
                                    .withName("memory")
                                    .withNewTargetAverageValue("512Mi")
                                .endResource()
                            .endMetric()
                            .addNewMetric()
                                .withType("Pods")
                                .withNewPods()
                                    .withMetricName("jobs")
                                    .withNewSelector()
                                        .addToMatchLabels("component", "worker")
                                    .endSelector()
                                    .withNewTargetAverageValue("10")
                                .endPods()
                            .endMetric()
                            .addNewMetric()
                                .withType("Object")
                                .withNewObject()
                                    .withNewTarget("batch/v1", "Job", "worker-job")
                                    .withMetricName("completions")
                                    .withNewTargetValue("5")
                                .endObject()
                            .endMetric()
                            .addNewMetric()
                                .withType("External")
                                .withNewExternal()
                                    .withMetricName("messages")
                                    .withNewMetricSelector()
                                        .addToMatchLabels("queue", "legacy")
                                    .endMetricSelector()
                                    .withNewTargetAverageValue("20")
                                .endExternal()
                            .endMetric()
                        .endSpec()
                        .withNewStatus()
                            .withCurrentReplicas(2)
                            .withDesiredReplicas(3)
                            .addNewCurrentMetric()
                                .withType("Resource")
                                .withNewResource()
                                    .withName("memory")
                                    .withCurrentAverageUtilization(75)
                                    .withNewCurrentAverageValue("384Mi")
                                .endResource()
                            .endCurrentMetric()
                        .endStatus()
                        .build();

        assertThat(autoscaler.getSpec().getMetrics()).hasSize(4);
        assertThat(autoscaler.getSpec().getMetrics().get(0).getResource().getTargetAverageValue().getAmount())
                .isEqualTo("512");
        assertThat(autoscaler.getSpec().getMetrics().get(0).getResource().getTargetAverageValue().getFormat())
                .isEqualTo("Mi");
        assertThat(autoscaler.getSpec().getMetrics().get(1).getPods().getSelector().getMatchLabels())
                .containsEntry("component", "worker");
        assertThat(autoscaler.getSpec().getMetrics().get(2).getObject().getTarget().getKind()).isEqualTo("Job");
        assertThat(autoscaler.getSpec().getMetrics().get(3).getExternal().getMetricSelector().getMatchLabels())
                .containsEntry("queue", "legacy");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(0).getResource().getCurrentAverageValue().getAmount())
                .isEqualTo("384");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(0).getResource().getCurrentAverageValue().getFormat())
                .isEqualTo("Mi");
    }

    @Test
    void v2beta2BuildersSupportContainerMetricsAdditionalPropertiesAndLists() {
        io.fabric8.kubernetes.api.model.autoscaling.v2beta2.HorizontalPodAutoscaler autoscaler =
                new io.fabric8.kubernetes.api.model.autoscaling.v2beta2.HorizontalPodAutoscalerBuilder()
                        .withApiVersion("autoscaling/v2beta2")
                        .withKind("HorizontalPodAutoscaler")
                        .withNewMetadata()
                            .withName("api-hpa")
                            .addToAnnotations("autoscaling.alpha.kubernetes.io/behavior", "enabled")
                        .endMetadata()
                        .withNewSpec()
                            .withNewScaleTargetRef("apps/v1", "Deployment", "api")
                            .withMinReplicas(3)
                            .withMaxReplicas(15)
                            .addNewMetric()
                                .withType("ContainerResource")
                                .withNewContainerResource()
                                    .withName("cpu")
                                    .withContainer("api")
                                    .withNewTarget()
                                        .withType("Utilization")
                                        .withAverageUtilization(65)
                                    .endTarget()
                                .endContainerResource()
                            .endMetric()
                            .addNewMetric()
                                .withType("Resource")
                                .withNewResource()
                                    .withName("memory")
                                    .withNewTarget()
                                        .withType("AverageValue")
                                        .withNewAverageValue("768Mi")
                                    .endTarget()
                                .endResource()
                            .endMetric()
                            .addToAdditionalProperties("custom-spec", Map.of("enabled", true))
                        .endSpec()
                        .withNewStatus()
                            .withCurrentReplicas(5)
                            .withDesiredReplicas(7)
                            .addNewCurrentMetric()
                                .withType("ContainerResource")
                                .withNewContainerResource()
                                    .withContainer("api")
                                    .withName("cpu")
                                    .withNewCurrent()
                                        .withAverageUtilization(62)
                                        .withNewAverageValue("620m")
                                    .endCurrent()
                                .endContainerResource()
                            .endCurrentMetric()
                        .endStatus()
                        .build();

        assertThat(autoscaler.getMetadata().getAnnotations())
                .containsEntry("autoscaling.alpha.kubernetes.io/behavior", "enabled");
        assertThat(autoscaler.getSpec().getMetrics().get(0).getContainerResource().getContainer()).isEqualTo("api");
        assertThat(autoscaler.getSpec().getMetrics().get(1).getResource().getTarget().getAverageValue().getAmount())
                .isEqualTo("768");
        assertThat(autoscaler.getSpec().getMetrics().get(1).getResource().getTarget().getAverageValue().getFormat())
                .isEqualTo("Mi");
        assertThat(autoscaler.getSpec().getAdditionalProperties()).containsKey("custom-spec");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(0).getContainerResource().getCurrent()
                .getAverageValue().getAmount()).isEqualTo("620");
        assertThat(autoscaler.getStatus().getCurrentMetrics().get(0).getContainerResource().getCurrent()
                .getAverageValue().getFormat()).isEqualTo("m");

        io.fabric8.kubernetes.api.model.autoscaling.v2beta2.HorizontalPodAutoscalerList list =
                new io.fabric8.kubernetes.api.model.autoscaling.v2beta2.HorizontalPodAutoscalerListBuilder()
                        .withApiVersion("autoscaling/v2beta2")
                        .withKind("HorizontalPodAutoscalerList")
                        .withItems(List.of(autoscaler))
                        .build();

        assertThat(list.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSpec().getScaleTargetRef().getName()).isEqualTo("api");
            assertThat(item.getSpec().getMetrics()).hasSize(2);
        });
    }
}
