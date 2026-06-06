/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_apps;

import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ListMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.ControllerRevision;
import io.fabric8.kubernetes.api.model.apps.ControllerRevisionBuilder;
import io.fabric8.kubernetes.api.model.apps.ControllerRevisionList;
import io.fabric8.kubernetes.api.model.apps.ControllerRevisionListBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSetCondition;
import io.fabric8.kubernetes.api.model.apps.DaemonSetConditionBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSetList;
import io.fabric8.kubernetes.api.model.apps.DaemonSetListBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.api.model.apps.DeploymentConditionBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.DeploymentListBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatusBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetCondition;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetConditionBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetList;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetListBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetCondition;
import io.fabric8.kubernetes.api.model.apps.StatefulSetConditionBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetList;
import io.fabric8.kubernetes.api.model.apps.StatefulSetListBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Kubernetes_model_appsTest {
    @Test
    void deploymentBuilderCreatesRollingUpdateTemplateStatusAndEditableCopies() {
        DeploymentCondition available = new DeploymentConditionBuilder()
                .withType("Available")
                .withStatus("True")
                .withReason("MinimumReplicasAvailable")
                .withMessage("Deployment has minimum availability")
                .withLastUpdateTime("2024-01-01T00:00:00Z")
                .withLastTransitionTime("2024-01-01T00:00:00Z")
                .build();

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                    .withName("checkout")
                    .withNamespace("production")
                    .addToLabels("app", "checkout")
                    .addToAnnotations("apps.fabric8.io/source", "test")
                .endMetadata()
                .withNewSpec()
                    .withReplicas(3)
                    .withMinReadySeconds(5)
                    .withProgressDeadlineSeconds(120)
                    .withRevisionHistoryLimit(4)
                    .withPaused(false)
                    .withNewSelector()
                        .addToMatchLabels("app", "checkout")
                    .endSelector()
                    .withNewStrategy()
                        .withType("RollingUpdate")
                        .withNewRollingUpdate()
                            .withNewMaxSurge("25%")
                            .withNewMaxUnavailable(0)
                            .addToAdditionalProperties("rollout-note", "surge-first")
                        .endRollingUpdate()
                    .endStrategy()
                    .withNewTemplate()
                        .withNewMetadata()
                            .addToLabels("app", "checkout")
                            .addToLabels("tier", "frontend")
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("web")
                                .withImage("example.test/checkout:stable")
                                .addNewPort()
                                    .withName("http")
                                    .withContainerPort(8080)
                                .endPort()
                                .addNewEnv()
                                    .withName("APP_MODE")
                                    .withValue("production")
                                .endEnv()
                                .addToAdditionalProperties("container-extension", "kept")
                            .endContainer()
                            .addNewImagePullSecret()
                                .withName("registry-secret")
                            .endImagePullSecret()
                        .endSpec()
                    .endTemplate()
                    .addToAdditionalProperties("spec-extension", "enabled")
                .endSpec()
                .withNewStatus()
                    .withObservedGeneration(7L)
                    .withReplicas(3)
                    .withUpdatedReplicas(3)
                    .withReadyReplicas(2)
                    .withAvailableReplicas(2)
                    .withUnavailableReplicas(1)
                    .withTerminatingReplicas(0)
                    .withCollisionCount(0)
                    .withConditions(available)
                    .addToAdditionalProperties("status-source", "controller")
                .endStatus()
                .addToAdditionalProperties("resource-extension", "deployment")
                .build();

        assertThat(deployment).isInstanceOf(HasMetadata.class);
        assertThat(deployment.getApiVersion()).isEqualTo("apps/v1");
        assertThat(deployment.getKind()).isEqualTo("Deployment");
        assertThat(deployment.getMetadata().getLabels()).containsEntry("app", "checkout");
        assertThat(deployment.getSpec().getSelector().getMatchLabels()).containsEntry("app", "checkout");
        assertThat(deployment.getSpec().getStrategy().getRollingUpdate().getMaxSurge().getStrVal()).isEqualTo("25%");
        assertThat(deployment.getSpec().getStrategy().getRollingUpdate().getMaxUnavailable().getIntVal()).isZero();
        assertThat(deployment.getSpec().getStrategy().getRollingUpdate().getAdditionalProperties())
                .containsEntry("rollout-note", "surge-first");
        assertThat(deployment.getSpec().getAdditionalProperties()).containsEntry("spec-extension", "enabled");
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        assertThat(container.getName()).isEqualTo("web");
        assertThat(container.getPorts().get(0).getContainerPort()).isEqualTo(8080);
        assertThat(container.getEnv().get(0).getValue()).isEqualTo("production");
        assertThat(container.getAdditionalProperties()).containsEntry("container-extension", "kept");
        assertThat(deployment.getStatus().getConditions()).containsExactly(available);
        assertThat(deployment.getStatus().getAdditionalProperties()).containsEntry("status-source", "controller");
        assertThat(deployment.getAdditionalProperties()).containsEntry("resource-extension", "deployment");

        Deployment scaled = deployment.toBuilder()
                .editSpec()
                    .withReplicas(5)
                    .editTemplate()
                        .editSpec()
                            .editFirstContainer()
                                .withImage("example.test/checkout:canary")
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .editStatus()
                    .withReplicas(5)
                    .withUpdatedReplicas(4)
                    .editMatchingCondition(condition -> "Available".equals(condition.getType()))
                        .withStatus("False")
                        .withReason("MinimumReplicasUnavailable")
                    .endCondition()
                .endStatus()
                .build();

        assertThat(scaled.getSpec().getReplicas()).isEqualTo(5);
        assertThat(scaled.getSpec().getTemplate().getSpec().getContainers().get(0).getImage())
                .endsWith(":canary");
        assertThat(scaled.getStatus().getConditions().get(0).getStatus()).isEqualTo("False");
        assertThat(deployment.getSpec().getReplicas()).isEqualTo(3);
        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage())
                .endsWith(":stable");

        DeploymentList list = new DeploymentListBuilder()
                .withMetadata(new ListMetaBuilder()
                        .withContinue("continue-token")
                        .withRemainingItemCount(2L)
                        .withResourceVersion("17")
                        .build())
                .withItems(deployment, scaled)
                .build();

        assertThat(list.getApiVersion()).isEqualTo("apps/v1");
        assertThat(list.getKind()).isEqualTo("DeploymentList");
        assertThat(list.getItems()).extracting(item -> item.getSpec().getReplicas()).containsExactly(3, 5);
    }

    @Test
    void replicaSetBuilderModelsSelectorsTemplatesStatusAndListPredicateEdits() {
        ReplicaSetCondition ready = new ReplicaSetConditionBuilder()
                .withType("ReplicaFailure")
                .withStatus("False")
                .withReason("ReplicasAvailable")
                .withMessage("all requested replicas are available")
                .withLastTransitionTime("2024-01-01T00:00:00Z")
                .build();
        ReplicaSet replicaSet = new ReplicaSetBuilder()
                .withNewMetadata()
                    .withName("checkout-7c9d")
                    .withNamespace("production")
                    .addToLabels("app", "checkout")
                    .addToLabels("pod-template-hash", "7c9d")
                .endMetadata()
                .withNewSpec()
                    .withReplicas(3)
                    .withMinReadySeconds(10)
                    .withNewSelector()
                        .addToMatchLabels("app", "checkout")
                        .addNewMatchExpression()
                            .withKey("track")
                            .withOperator("In")
                            .withValues("stable", "canary")
                        .endMatchExpression()
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .addToLabels("app", "checkout")
                            .addToLabels("track", "stable")
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("worker")
                                .withImage("example.test/checkout-worker:stable")
                                .addNewEnv()
                                    .withName("QUEUE")
                                    .withValue("orders")
                                .endEnv()
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                    .addToAdditionalProperties("owner", "deployment")
                .endSpec()
                .withNewStatus()
                    .withObservedGeneration(4L)
                    .withReplicas(3)
                    .withFullyLabeledReplicas(3)
                    .withReadyReplicas(3)
                    .withAvailableReplicas(3)
                    .withConditions(ready)
                .endStatus()
                .build();

        assertThat(replicaSet.getApiVersion()).isEqualTo("apps/v1");
        assertThat(replicaSet.getKind()).isEqualTo("ReplicaSet");
        assertThat(replicaSet.getSpec().getSelector().getMatchExpressions().get(0).getValues())
                .containsExactly("stable", "canary");
        assertThat(replicaSet.getSpec().getTemplate().getMetadata().getLabels()).containsEntry("track", "stable");
        assertThat(replicaSet.getSpec().getAdditionalProperties()).containsEntry("owner", "deployment");
        assertThat(replicaSet.getStatus().getFullyLabeledReplicas()).isEqualTo(3);
        assertThat(replicaSet.getStatus().getConditions()).containsExactly(ready);

        ReplicaSet canary = replicaSet.toBuilder()
                .editMetadata()
                    .withName("checkout-8d10")
                    .addToLabels("pod-template-hash", "8d10")
                .endMetadata()
                .editSpec()
                    .withReplicas(1)
                    .editTemplate()
                        .editMetadata()
                            .addToLabels("track", "canary")
                        .endMetadata()
                    .endTemplate()
                .endSpec()
                .editStatus()
                    .withReplicas(1)
                    .withAvailableReplicas(0)
                    .withReadyReplicas(0)
                .endStatus()
                .build();
        ReplicaSetList list = new ReplicaSetListBuilder()
                .withMetadata(new ListMetaBuilder()
                        .withRemainingItemCount(2L)
                        .withResourceVersion("21")
                        .build())
                .withItems(replicaSet, canary)
                .editMatchingItem(item -> "checkout-8d10".equals(item.buildMetadata().getName()))
                    .editStatus()
                        .withAvailableReplicas(1)
                    .endStatus()
                .endItem()
                .build();

        assertThat(list.getKind()).isEqualTo("ReplicaSetList");
        assertThat(list.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("checkout-7c9d", "checkout-8d10");
        assertThat(list.getItems().get(1).getStatus().getAvailableReplicas()).isEqualTo(1);
        assertThat(canary.getStatus().getAvailableReplicas()).isZero();
    }

    @Test
    void daemonSetBuilderCreatesRollingUpdatesTemplateStatusAndRemovableListItems() {
        DaemonSetCondition progressing = new DaemonSetConditionBuilder()
                .withType("Progressing")
                .withStatus("True")
                .withReason("RollingUpdate")
                .withMessage("daemon pods are rolling")
                .build();
        DaemonSet daemonSet = new DaemonSetBuilder()
                .withNewMetadata()
                    .withName("node-log-agent")
                    .withNamespace("platform")
                    .addToLabels("app", "log-agent")
                .endMetadata()
                .withNewSpec()
                    .withMinReadySeconds(3)
                    .withRevisionHistoryLimit(6)
                    .withNewSelector()
                        .addToMatchLabels("app", "log-agent")
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .addToLabels("app", "log-agent")
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("collector")
                                .withImage("example.test/log-agent:stable")
                                .addNewVolumeMount()
                                    .withName("varlog")
                                    .withMountPath("/var/log")
                                    .withReadOnly(true)
                                .endVolumeMount()
                            .endContainer()
                            .addNewVolume()
                                .withName("varlog")
                                .withNewHostPath()
                                    .withPath("/var/log")
                                    .withType("Directory")
                                .endHostPath()
                            .endVolume()
                        .endSpec()
                    .endTemplate()
                    .withNewUpdateStrategy()
                        .withType("RollingUpdate")
                        .withNewRollingUpdate()
                            .withNewMaxUnavailable("20%")
                            .withNewMaxSurge(1)
                        .endRollingUpdate()
                    .endUpdateStrategy()
                .endSpec()
                .withNewStatus()
                    .withObservedGeneration(9L)
                    .withDesiredNumberScheduled(10)
                    .withCurrentNumberScheduled(10)
                    .withUpdatedNumberScheduled(8)
                    .withNumberReady(8)
                    .withNumberAvailable(8)
                    .withNumberUnavailable(2)
                    .withNumberMisscheduled(0)
                    .withCollisionCount(0)
                    .withConditions(progressing)
                .endStatus()
                .addToAdditionalProperties("controller", "daemonset")
                .build();

        assertThat(daemonSet.getApiVersion()).isEqualTo("apps/v1");
        assertThat(daemonSet.getKind()).isEqualTo("DaemonSet");
        assertThat(daemonSet.getSpec().getUpdateStrategy().getRollingUpdate().getMaxUnavailable().getStrVal())
                .isEqualTo("20%");
        assertThat(daemonSet.getSpec().getUpdateStrategy().getRollingUpdate().getMaxSurge().getIntVal()).isEqualTo(1);
        assertThat(daemonSet.getSpec().getTemplate().getSpec().getVolumes().get(0).getHostPath().getPath())
                .isEqualTo("/var/log");
        assertThat(daemonSet.getStatus().getConditions()).containsExactly(progressing);
        assertThat(daemonSet.getAdditionalProperties()).containsEntry("controller", "daemonset");

        DaemonSet paused = daemonSet.toBuilder()
                .editMetadata()
                    .withName("node-log-agent-paused")
                .endMetadata()
                .editSpec()
                    .editUpdateStrategy()
                        .withType("OnDelete")
                    .endUpdateStrategy()
                .endSpec()
                .build();
        DaemonSetList list = new DaemonSetListBuilder()
                .withMetadata(new ListMetaBuilder()
                        .withRemainingItemCount(2L)
                        .withResourceVersion("31")
                        .build())
                .withItems(daemonSet, paused)
                .removeMatchingFromItems(item -> item.buildMetadata().getName().endsWith("paused"))
                .build();

        assertThat(paused.getSpec().getUpdateStrategy().getType()).isEqualTo("OnDelete");
        assertThat(daemonSet.getSpec().getUpdateStrategy().getType()).isEqualTo("RollingUpdate");
        assertThat(list.getKind()).isEqualTo("DaemonSetList");
        assertThat(list.getItems()).singleElement().satisfies(item ->
                assertThat(item.getMetadata().getName()).isEqualTo("node-log-agent"));
    }

    @Test
    void statefulSetBuilderModelsOrdinalsRetentionPoliciesRollingUpdatesAndClaims() {
        StatefulSetCondition ready = new StatefulSetConditionBuilder()
                .withType("Ready")
                .withStatus("True")
                .withReason("AllReplicasReady")
                .withMessage("all database replicas are ready")
                .build();
        StatefulSet statefulSet = new StatefulSetBuilder()
                .withNewMetadata()
                    .withName("orders-db")
                    .withNamespace("production")
                    .addToLabels("app", "orders-db")
                .endMetadata()
                .withNewSpec()
                    .withServiceName("orders-db-headless")
                    .withReplicas(3)
                    .withPodManagementPolicy("Parallel")
                    .withMinReadySeconds(15)
                    .withRevisionHistoryLimit(5)
                    .withNewOrdinals(1)
                    .withNewPersistentVolumeClaimRetentionPolicy("Retain", "Delete")
                    .withNewSelector()
                        .addToMatchLabels("app", "orders-db")
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .addToLabels("app", "orders-db")
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("postgres")
                                .withImage("example.test/postgres:stable")
                                .addNewPort()
                                    .withName("postgres")
                                    .withContainerPort(5432)
                                .endPort()
                                .addNewVolumeMount()
                                    .withName("data")
                                    .withMountPath("/var/lib/postgresql/data")
                                .endVolumeMount()
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                    .addNewVolumeClaimTemplate()
                        .withNewMetadata()
                            .withName("data")
                        .endMetadata()
                        .withNewSpec()
                            .withAccessModes("ReadWriteOnce")
                        .endSpec()
                    .endVolumeClaimTemplate()
                    .withNewUpdateStrategy()
                        .withType("RollingUpdate")
                        .withNewRollingUpdate()
                            .withPartition(1)
                            .withNewMaxUnavailable(1)
                        .endRollingUpdate()
                    .endUpdateStrategy()
                    .addToAdditionalProperties("database", "orders")
                .endSpec()
                .withNewStatus()
                    .withObservedGeneration(11L)
                    .withReplicas(3)
                    .withReadyReplicas(3)
                    .withCurrentReplicas(2)
                    .withUpdatedReplicas(1)
                    .withAvailableReplicas(3)
                    .withCurrentRevision("orders-db-7c9d")
                    .withUpdateRevision("orders-db-8d10")
                    .withCollisionCount(0)
                    .withConditions(ready)
                .endStatus()
                .build();

        assertThat(statefulSet.getApiVersion()).isEqualTo("apps/v1");
        assertThat(statefulSet.getKind()).isEqualTo("StatefulSet");
        assertThat(statefulSet.getSpec().getServiceName()).isEqualTo("orders-db-headless");
        assertThat(statefulSet.getSpec().getOrdinals().getStart()).isEqualTo(1);
        assertThat(statefulSet.getSpec().getPersistentVolumeClaimRetentionPolicy().getWhenDeleted())
                .isEqualTo("Retain");
        assertThat(statefulSet.getSpec().getPersistentVolumeClaimRetentionPolicy().getWhenScaled())
                .isEqualTo("Delete");
        assertThat(statefulSet.getSpec().getVolumeClaimTemplates().get(0).getMetadata().getName()).isEqualTo("data");
        assertThat(statefulSet.getSpec().getVolumeClaimTemplates().get(0).getSpec().getAccessModes())
                .containsExactly("ReadWriteOnce");
        assertThat(statefulSet.getSpec().getUpdateStrategy().getRollingUpdate().getPartition()).isEqualTo(1);
        assertThat(statefulSet.getSpec().getUpdateStrategy().getRollingUpdate().getMaxUnavailable().getIntVal())
                .isEqualTo(1);
        assertThat(statefulSet.getSpec().getAdditionalProperties()).containsEntry("database", "orders");
        assertThat(statefulSet.getStatus().getConditions()).containsExactly(ready);

        StatefulSet expanded = statefulSet.toBuilder()
                .editSpec()
                    .withReplicas(5)
                    .editOrdinals()
                        .withStart(0)
                    .endOrdinals()
                    .editUpdateStrategy()
                        .editRollingUpdate()
                            .withPartition(0)
                        .endRollingUpdate()
                    .endUpdateStrategy()
                .endSpec()
                .editStatus()
                    .withReplicas(5)
                    .withReadyReplicas(4)
                .endStatus()
                .build();
        StatefulSetList list = new StatefulSetListBuilder()
                .withMetadata(new ListMetaBuilder()
                        .withContinue("next")
                        .withRemainingItemCount(2L)
                        .withResourceVersion("41")
                        .build())
                .withItems(statefulSet, expanded)
                .build();

        assertThat(expanded.getSpec().getReplicas()).isEqualTo(5);
        assertThat(expanded.getSpec().getOrdinals().getStart()).isZero();
        assertThat(statefulSet.getSpec().getOrdinals().getStart()).isEqualTo(1);
        assertThat(list.getKind()).isEqualTo("StatefulSetList");
        assertThat(list.getItems()).extracting(item -> item.getStatus().getReadyReplicas())
                .containsExactly(3, 4);
    }

    @Test
    void controllerRevisionBuilderPreservesRevisionDataAdditionalPropertiesAndLists() {
        ControllerRevision revision = new ControllerRevisionBuilder()
                .withNewMetadata()
                    .withName("checkout-7c9d")
                    .withNamespace("production")
                    .addToLabels("app", "checkout")
                .endMetadata()
                .withRevision(7L)
                .withData(Map.of(
                        "apiVersion", "apps/v1",
                        "kind", "ControllerRevision",
                        "controller", Map.of("name", "checkout")))
                .addToAdditionalProperties("history", "stable")
                .build();

        assertThat(revision.getApiVersion()).isEqualTo("apps/v1");
        assertThat(revision.getKind()).isEqualTo("ControllerRevision");
        assertThat(revision.getRevision()).isEqualTo(7L);
        assertThat(revision.getData()).isInstanceOf(Map.class);
        assertThat(revision.getAdditionalProperties()).containsEntry("history", "stable");

        ControllerRevision nextRevision = revision.toBuilder()
                .editMetadata()
                    .withName("checkout-8d10")
                .endMetadata()
                .withRevision(8L)
                .addToAdditionalProperties("history", "canary")
                .build();
        ControllerRevisionList list = new ControllerRevisionListBuilder()
                .withMetadata(new ListMetaBuilder()
                        .withRemainingItemCount(2L)
                        .withResourceVersion("51")
                        .build())
                .withItems(revision, nextRevision)
                .editMatchingItem(item -> "checkout-8d10".equals(item.buildMetadata().getName()))
                    .addToAdditionalProperties("promoted", Boolean.TRUE)
                .endItem()
                .build();

        assertThat(nextRevision.getRevision()).isEqualTo(8L);
        assertThat(revision.getRevision()).isEqualTo(7L);
        assertThat(list.getApiVersion()).isEqualTo("apps/v1");
        assertThat(list.getKind()).isEqualTo("ControllerRevisionList");
        assertThat(list.getItems()).extracting(ControllerRevision::getRevision).containsExactly(7L, 8L);
        assertThat(list.getItems().get(1).getAdditionalProperties()).containsEntry("promoted", Boolean.TRUE);
    }

    @Test
    void deploymentListBuilderVisitorUpdatesNestedContainers() {
        DeploymentList list = new DeploymentListBuilder()
                .addNewItem()
                    .withNewMetadata()
                        .withName("checkout")
                        .withNamespace("production")
                        .addToLabels("app", "checkout")
                    .endMetadata()
                    .withNewSpec()
                        .withReplicas(2)
                        .withNewSelector()
                            .addToMatchLabels("app", "checkout")
                        .endSelector()
                        .withNewTemplate()
                            .withNewMetadata()
                                .addToLabels("app", "checkout")
                            .endMetadata()
                            .withNewSpec()
                                .addNewContainer()
                                    .withName("api")
                                    .withImage("example.test/checkout-api:stable")
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endItem()
                .addNewItem()
                    .withNewMetadata()
                        .withName("checkout-worker")
                        .withNamespace("production")
                        .addToLabels("app", "checkout-worker")
                    .endMetadata()
                    .withNewSpec()
                        .withReplicas(1)
                        .withNewSelector()
                            .addToMatchLabels("app", "checkout-worker")
                        .endSelector()
                        .withNewTemplate()
                            .withNewMetadata()
                                .addToLabels("app", "checkout-worker")
                            .endMetadata()
                            .withNewSpec()
                                .addNewContainer()
                                    .withName("worker")
                                    .withImage("example.test/checkout-worker:stable")
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endItem()
                .accept(new Visitor<ContainerBuilder>() {
                    @Override
                    public Class<ContainerBuilder> getType() {
                        return ContainerBuilder.class;
                    }

                    @Override
                    public void visit(ContainerBuilder container) {
                        container.addNewEnv()
                                .withName("CLUSTER_NAME")
                                .withValue("production")
                                .endEnv();
                        if ("api".equals(container.getName())) {
                            container.addToAdditionalProperties("visited-by", "container-visitor");
                        }
                    }
                })
                .build();

        assertThat(list.getItems()).hasSize(2);
        assertThat(list.getItems())
                .extracting(item -> item.getSpec().getTemplate().getSpec().getContainers().get(0).getName())
                .containsExactly("api", "worker");
        assertThat(list.getItems())
                .flatExtracting(item -> item.getSpec().getTemplate().getSpec().getContainers())
                .allSatisfy(container -> assertThat(container.getEnv())
                        .singleElement()
                        .satisfies(env -> {
                            assertThat(env.getName()).isEqualTo("CLUSTER_NAME");
                            assertThat(env.getValue()).isEqualTo("production");
                        }));
        assertThat(list.getItems().get(0).getSpec().getTemplate().getSpec().getContainers().get(0)
                .getAdditionalProperties()).containsEntry("visited-by", "container-visitor");
        assertThat(list.getItems().get(1).getSpec().getTemplate().getSpec().getContainers().get(0)
                .getAdditionalProperties()).doesNotContainKey("visited-by");
    }

    @Test
    void editOrNewLikeUsesFallbacksOnlyForMissingDeploymentSections() {
        Deployment deployment = new DeploymentBuilder()
                .addToAdditionalProperties("discarded", "yes")
                .addToAdditionalProperties("retained", "yes")
                .editOrNewMetadata()
                    .withName("edit-or-new")
                    .withNamespace("production")
                .endMetadata()
                .editOrNewSpecLike(new DeploymentSpecBuilder()
                        .withReplicas(2)
                        .withMinReadySeconds(5)
                        .withNewSelector()
                            .addToMatchLabels("app", "edit-or-new")
                        .endSelector()
                        .build())
                    .withReplicas(3)
                .endSpec()
                .editOrNewStatusLike(new DeploymentStatusBuilder()
                        .withObservedGeneration(1L)
                        .withReplicas(3)
                        .build())
                    .withReadyReplicas(2)
                .endStatus()
                .editOrNewSpecLike(new DeploymentSpecBuilder()
                        .withReplicas(99)
                        .withMinReadySeconds(99)
                        .build())
                    .withRevisionHistoryLimit(6)
                .endSpec()
                .editOrNewStatusLike(new DeploymentStatusBuilder()
                        .withObservedGeneration(99L)
                        .build())
                    .withAvailableReplicas(2)
                .endStatus()
                .removeFromAdditionalProperties("discarded")
                .build();

        assertThat(deployment.getApiVersion()).isEqualTo("apps/v1");
        assertThat(deployment.getKind()).isEqualTo("Deployment");
        assertThat(deployment.getMetadata().getName()).isEqualTo("edit-or-new");
        assertThat(deployment.getSpec().getReplicas()).isEqualTo(3);
        assertThat(deployment.getSpec().getMinReadySeconds()).isEqualTo(5);
        assertThat(deployment.getSpec().getRevisionHistoryLimit()).isEqualTo(6);
        assertThat(deployment.getSpec().getSelector().getMatchLabels()).containsEntry("app", "edit-or-new");
        assertThat(deployment.getStatus().getObservedGeneration()).isEqualTo(1L);
        assertThat(deployment.getStatus().getReadyReplicas()).isEqualTo(2);
        assertThat(deployment.getStatus().getAvailableReplicas()).isEqualTo(2);
        assertThat(deployment.getAdditionalProperties())
                .containsEntry("retained", "yes")
                .doesNotContainKey("discarded");
    }

    @Test
    void serviceLoaderDiscoversAppsResources() {
        List<String> discoveredApiKinds = new ArrayList<>();
        for (KubernetesResource resource : ServiceLoader.load(KubernetesResource.class)) {
            if (resource instanceof HasMetadata metadata) {
                String apiVersion = metadata.getApiVersion();
                if ("apps/v1".equals(apiVersion)) {
                    discoveredApiKinds.add(apiVersion + "/" + metadata.getKind());
                }
            }
        }

        assertThat(discoveredApiKinds)
                .contains("apps/v1/ControllerRevision")
                .contains("apps/v1/DaemonSet")
                .contains("apps/v1/Deployment")
                .contains("apps/v1/ReplicaSet")
                .contains("apps/v1/StatefulSet");
    }
}
