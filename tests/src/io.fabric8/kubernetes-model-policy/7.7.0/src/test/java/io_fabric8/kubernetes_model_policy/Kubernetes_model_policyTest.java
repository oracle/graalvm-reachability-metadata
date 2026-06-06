/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_policy;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.DeleteOptionsBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.ListMetaBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.Eviction;
import io.fabric8.kubernetes.api.model.policy.v1.EvictionBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetList;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetListBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.AllowedCSIDriverBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.AllowedFlexVolumeBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.AllowedHostPath;
import io.fabric8.kubernetes.api.model.policy.v1beta1.FSGroupStrategyOptionsBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.HostPortRange;
import io.fabric8.kubernetes.api.model.policy.v1beta1.HostPortRangeBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.IDRangeBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.PodSecurityPolicy;
import io.fabric8.kubernetes.api.model.policy.v1beta1.PodSecurityPolicyBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.PodSecurityPolicyList;
import io.fabric8.kubernetes.api.model.policy.v1beta1.PodSecurityPolicyListBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.PodSecurityPolicySpec;
import io.fabric8.kubernetes.api.model.policy.v1beta1.PodSecurityPolicySpecBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.RunAsGroupStrategyOptionsBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.RunAsUserStrategyOptionsBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.RuntimeClassStrategyOptionsBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.SELinuxStrategyOptionsBuilder;
import io.fabric8.kubernetes.api.model.policy.v1beta1.SupplementalGroupsStrategyOptionsBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Kubernetes_model_policyTest {
    @Test
    void buildsAndEditsPolicyV1PodDisruptionBudget() {
        Condition condition = new ConditionBuilder()
                .withType("DisruptionAllowed")
                .withStatus("True")
                .withReason("SufficientPods")
                .withMessage("budget permits one disruption")
                .withObservedGeneration(9L)
                .build();

        PodDisruptionBudget budget = new PodDisruptionBudgetBuilder()
                .withApiVersion("policy/v1")
                .withKind("PodDisruptionBudget")
                .withNewMetadata()
                    .withName("web-budget")
                    .withNamespace("production")
                    .addToLabels("app", "web")
                .endMetadata()
                .withNewSpec()
                    .withMaxUnavailable(new IntOrString("25%"))
                    .withMinAvailable(new IntOrString(2))
                    .withSelector(new LabelSelectorBuilder().addToMatchLabels("app", "web").build())
                    .withUnhealthyPodEvictionPolicy("IfHealthyBudget")
                .endSpec()
                .withNewStatus()
                    .withCurrentHealthy(4)
                    .withDesiredHealthy(3)
                    .withDisruptionsAllowed(1)
                    .withExpectedPods(5)
                    .withObservedGeneration(9L)
                    .addToDisruptedPods("web-0", "2026-01-01T00:00:00Z")
                    .withConditions(condition)
                .endStatus()
                .addToAdditionalProperties("x-test", "retained")
                .build();

        assertThat(budget).isInstanceOf(KubernetesResource.class);
        assertThat(budget.getApiVersion()).isEqualTo("policy/v1");
        assertThat(budget.getKind()).isEqualTo("PodDisruptionBudget");
        assertThat(budget.getMetadata().getName()).isEqualTo("web-budget");
        assertThat(budget.getMetadata().getLabels()).containsEntry("app", "web");
        assertThat(budget.getSpec().getMaxUnavailable().getStrVal()).isEqualTo("25%");
        assertThat(budget.getSpec().getMinAvailable().getIntVal()).isEqualTo(2);
        assertThat(budget.getSpec().getSelector().getMatchLabels()).containsEntry("app", "web");
        assertThat(budget.getSpec().getUnhealthyPodEvictionPolicy()).isEqualTo("IfHealthyBudget");
        assertThat(budget.getStatus().getConditions()).containsExactly(condition);
        assertThat(budget.getStatus().getDisruptedPods()).containsEntry("web-0", "2026-01-01T00:00:00Z");
        assertThat(budget.getAdditionalProperties()).containsEntry("x-test", "retained");

        PodDisruptionBudget edited = budget.toBuilder()
                .editSpec()
                    .withMinAvailable(new IntOrString(3))
                .endSpec()
                .editStatus()
                    .withCurrentHealthy(5)
                    .removeFromDisruptedPods("web-0")
                .endStatus()
                .build();

        assertThat(edited.getSpec().getMinAvailable().getIntVal()).isEqualTo(3);
        assertThat(edited.getStatus().getCurrentHealthy()).isEqualTo(5);
        assertThat(edited.getStatus().getDisruptedPods()).doesNotContainKey("web-0");
        assertThat(edited.getMetadata().getName()).isEqualTo(budget.getMetadata().getName());
    }

    @Test
    void buildsPolicyV1PodDisruptionBudgetListWithNestedItems() {
        PodDisruptionBudget webBudget = new PodDisruptionBudgetBuilder()
                .withApiVersion("policy/v1")
                .withKind("PodDisruptionBudget")
                .withNewMetadata()
                    .withName("web-budget")
                .endMetadata()
                .withNewSpec()
                    .withMinAvailable(new IntOrString("50%"))
                .endSpec()
                .build();

        PodDisruptionBudgetList list = new PodDisruptionBudgetListBuilder()
                .withApiVersion("policy/v1")
                .withKind("PodDisruptionBudgetList")
                .withMetadata(new ListMetaBuilder()
                        .withContinue("continue-token")
                        .withRemainingItemCount(123L)
                        .withResourceVersion("42")
                        .build())
                .addToItems(webBudget)
                .addNewItem()
                    .withApiVersion("policy/v1")
                    .withKind("PodDisruptionBudget")
                    .withNewMetadata()
                        .withName("api-budget")
                    .endMetadata()
                    .withNewSpec()
                        .withMaxUnavailable(new IntOrString(1))
                    .endSpec()
                .endItem()
                .build();

        assertThat(list).isInstanceOf(KubernetesResource.class);
        assertThat(list.getMetadata().getContinue()).isEqualTo("continue-token");
        assertThat(list.getMetadata().getRemainingItemCount()).isEqualTo(123L);
        assertThat(list.getMetadata().getResourceVersion()).isEqualTo("42");
        assertThat(list.getItems()).hasSize(2);
        assertThat(list.getItems().get(0).getMetadata().getName()).isEqualTo("web-budget");
        assertThat(list.getItems().get(1).getSpec().getMaxUnavailable().getIntVal()).isEqualTo(1);
        assertThat(new PodDisruptionBudgetListBuilder(list).buildMatchingItem(
                item -> "api-budget".equals(item.buildMetadata().getName())))
                .isEqualTo(list.getItems().get(1));
    }

    @Test
    void buildsPolicyV1EvictionWithDeleteOptions() {
        Eviction eviction = new EvictionBuilder()
                .withApiVersion("policy/v1")
                .withKind("Eviction")
                .withNewMetadata()
                    .withName("web-0")
                    .withNamespace("production")
                .endMetadata()
                .withDeleteOptions(new DeleteOptionsBuilder()
                        .withGracePeriodSeconds(30L)
                        .withNewPreconditions(null, "pod-uid")
                        .build())
                .addToAdditionalProperties("requested-by", "test-suite")
                .build();

        assertThat(eviction).isInstanceOf(KubernetesResource.class);
        assertThat(eviction.getMetadata().getName()).isEqualTo("web-0");
        assertThat(eviction.getDeleteOptions().getGracePeriodSeconds()).isEqualTo(30L);
        assertThat(eviction.getDeleteOptions().getPreconditions().getUid()).isEqualTo("pod-uid");
        assertThat(eviction.getAdditionalProperties()).containsEntry("requested-by", "test-suite");
    }

    @Test
    void editsPolicyV1Beta1PodDisruptionBudgetListWithPredicateRemoval() {
        Condition blockedCondition = new ConditionBuilder()
                .withType("DisruptionAllowed")
                .withStatus("False")
                .withReason("InsufficientPods")
                .withMessage("no pods can be disrupted")
                .build();

        io.fabric8.kubernetes.api.model.policy.v1beta1.PodDisruptionBudget frontendBudget =
                new io.fabric8.kubernetes.api.model.policy.v1beta1.PodDisruptionBudgetBuilder()
                        .withApiVersion("policy/v1beta1")
                        .withKind("PodDisruptionBudget")
                        .withNewMetadata()
                            .withName("frontend-budget")
                            .addToLabels("tier", "frontend")
                        .endMetadata()
                        .withNewSpec()
                            .withMinAvailable(new IntOrString("60%"))
                            .withSelector(new LabelSelectorBuilder().addToMatchLabels("tier", "frontend").build())
                        .endSpec()
                        .withNewStatus()
                            .withCurrentHealthy(2)
                            .withDesiredHealthy(3)
                            .withDisruptionsAllowed(0)
                            .withExpectedPods(3)
                            .withConditions(blockedCondition)
                        .endStatus()
                        .build();

        io.fabric8.kubernetes.api.model.policy.v1beta1.PodDisruptionBudgetList list =
                new io.fabric8.kubernetes.api.model.policy.v1beta1.PodDisruptionBudgetListBuilder()
                        .withApiVersion("policy/v1beta1")
                        .withKind("PodDisruptionBudgetList")
                        .addToItems(frontendBudget)
                        .addNewItem()
                            .withApiVersion("policy/v1beta1")
                            .withKind("PodDisruptionBudget")
                            .withNewMetadata()
                                .withName("api-budget")
                                .addToLabels("tier", "backend")
                            .endMetadata()
                            .withNewSpec()
                                .withMaxUnavailable(new IntOrString(1))
                                .withSelector(new LabelSelectorBuilder().addToMatchLabels("tier", "backend").build())
                            .endSpec()
                            .withNewStatus()
                                .withCurrentHealthy(4)
                                .withDesiredHealthy(4)
                                .withDisruptionsAllowed(0)
                                .withExpectedPods(5)
                                .addToDisruptedPods("api-0", "2026-01-01T00:00:00Z")
                                .withConditions(blockedCondition)
                            .endStatus()
                        .endItem()
                        .build();

        io.fabric8.kubernetes.api.model.policy.v1beta1.PodDisruptionBudgetList edited =
                new io.fabric8.kubernetes.api.model.policy.v1beta1.PodDisruptionBudgetListBuilder(list)
                        .editMatchingItem(item -> "api-budget".equals(item.buildMetadata().getName()))
                            .editSpec()
                                .withMaxUnavailable(new IntOrString("20%"))
                            .endSpec()
                            .editStatus()
                                .withDisruptionsAllowed(1)
                                .removeFromDisruptedPods("api-0")
                            .endStatus()
                        .endItem()
                        .removeMatchingFromItems(item -> "frontend".equals(
                                item.buildSpec().getSelector().getMatchLabels().get("tier")))
                        .build();

        assertThat(edited).isInstanceOf(KubernetesResource.class);
        assertThat(edited.getItems()).hasSize(1);

        io.fabric8.kubernetes.api.model.policy.v1beta1.PodDisruptionBudget remainingBudget =
                edited.getItems().get(0);
        assertThat(remainingBudget.getApiVersion()).isEqualTo("policy/v1beta1");
        assertThat(remainingBudget.getMetadata().getName()).isEqualTo("api-budget");
        assertThat(remainingBudget.getMetadata().getLabels()).containsEntry("tier", "backend");
        assertThat(remainingBudget.getSpec().getMaxUnavailable().getStrVal()).isEqualTo("20%");
        assertThat(remainingBudget.getSpec().getSelector().getMatchLabels()).containsEntry("tier", "backend");
        assertThat(remainingBudget.getStatus().getDisruptionsAllowed()).isEqualTo(1);
        assertThat(remainingBudget.getStatus().getDisruptedPods()).doesNotContainKey("api-0");
        assertThat(remainingBudget.getStatus().getConditions()).containsExactly(blockedCondition);
    }

    @Test
    void buildsV1Beta1PodSecurityPolicyWithNestedSecurityStrategies() {
        PodSecurityPolicySpec spec = new PodSecurityPolicySpecBuilder()
                .withPrivileged(false)
                .withAllowPrivilegeEscalation(false)
                .withDefaultAllowPrivilegeEscalation(false)
                .withReadOnlyRootFilesystem(true)
                .withHostNetwork(false)
                .withHostPID(false)
                .withHostIPC(false)
                .withAllowedCapabilities("NET_BIND_SERVICE")
                .withDefaultAddCapabilities("AUDIT_WRITE")
                .withRequiredDropCapabilities("ALL")
                .withAllowedProcMountTypes("Default")
                .withAllowedUnsafeSysctls("kernel.shm*", "net.ipv4.ip_local_port_range")
                .withForbiddenSysctls("kernel.msg*", "net.*")
                .withVolumes("configMap", "secret", "emptyDir")
                .withAllowedCSIDrivers(new AllowedCSIDriverBuilder().withName("csi.example.com").build())
                .withAllowedFlexVolumes(new AllowedFlexVolumeBuilder().withDriver("example.com/flex").build())
                .addNewAllowedHostPath("/var/log", true)
                .addToHostPorts(new HostPortRangeBuilder().withMin(30000).withMax(32767).build())
                .withFsGroup(new FSGroupStrategyOptionsBuilder()
                        .withRule("MustRunAs")
                        .addToRanges(new IDRangeBuilder().withMin(1L).withMax(65535L).build())
                        .build())
                .withRunAsGroup(new RunAsGroupStrategyOptionsBuilder()
                        .withRule("MustRunAs")
                        .addToRanges(new IDRangeBuilder().withMin(1000L).withMax(2000L).build())
                        .build())
                .withRunAsUser(new RunAsUserStrategyOptionsBuilder()
                        .withRule("MustRunAsNonRoot")
                        .addToRanges(new IDRangeBuilder().withMin(1000L).withMax(2000L).build())
                        .build())
                .withRuntimeClass(new RuntimeClassStrategyOptionsBuilder()
                        .withDefaultRuntimeClassName("runc")
                        .withAllowedRuntimeClassNames("runc", "gvisor")
                        .build())
                .withSeLinux(new SELinuxStrategyOptionsBuilder()
                        .withRule("RunAsAny")
                        .withNewSeLinuxOptions("system_u", "system_r", "container_t", "s0")
                        .build())
                .withSupplementalGroups(new SupplementalGroupsStrategyOptionsBuilder()
                        .withRule("MustRunAs")
                        .addToRanges(new IDRangeBuilder().withMin(1L).withMax(65535L).build())
                        .build())
                .addToAdditionalProperties("policy-owner", "platform")
                .build();

        PodSecurityPolicy policy = new PodSecurityPolicyBuilder()
                .withApiVersion("policy/v1beta1")
                .withKind("PodSecurityPolicy")
                .withNewMetadata()
                    .withName("restricted")
                    .addToAnnotations("seccomp.security.alpha.kubernetes.io/allowedProfileNames", "runtime/default")
                .endMetadata()
                .withSpec(spec)
                .build();

        assertThat(policy).isInstanceOf(KubernetesResource.class);
        assertThat(policy.getMetadata().getName()).isEqualTo("restricted");
        assertThat(policy.getSpec().getPrivileged()).isFalse();
        assertThat(policy.getSpec().getReadOnlyRootFilesystem()).isTrue();
        assertThat(policy.getSpec().getAllowedCapabilities()).containsExactly("NET_BIND_SERVICE");
        assertThat(policy.getSpec().getRequiredDropCapabilities()).containsExactly("ALL");
        assertThat(policy.getSpec().getAllowedUnsafeSysctls()).contains("kernel.shm*");
        assertThat(policy.getSpec().getForbiddenSysctls()).contains("net.*");
        assertThat(policy.getSpec().getAllowedCSIDrivers().get(0).getName()).isEqualTo("csi.example.com");
        assertThat(policy.getSpec().getAllowedFlexVolumes().get(0).getDriver()).isEqualTo("example.com/flex");

        AllowedHostPath hostPath = policy.getSpec().getAllowedHostPaths().get(0);
        assertThat(hostPath.getPathPrefix()).isEqualTo("/var/log");
        assertThat(hostPath.getReadOnly()).isTrue();

        HostPortRange hostPort = policy.getSpec().getHostPorts().get(0);
        assertThat(hostPort.getMin()).isEqualTo(30000);
        assertThat(hostPort.getMax()).isEqualTo(32767);
        assertThat(policy.getSpec().getFsGroup().getRanges().get(0).getMax()).isEqualTo(65535L);
        assertThat(policy.getSpec().getRunAsGroup().getRanges().get(0).getMin()).isEqualTo(1000L);
        assertThat(policy.getSpec().getRunAsUser().getRule()).isEqualTo("MustRunAsNonRoot");
        assertThat(policy.getSpec().getRuntimeClass().getAllowedRuntimeClassNames()).containsExactly("runc", "gvisor");
        assertThat(policy.getSpec().getSeLinux().getSeLinuxOptions().getType()).isEqualTo("container_t");
        assertThat(policy.getSpec().getSupplementalGroups().getRule()).isEqualTo("MustRunAs");
        assertThat(policy.getSpec().getAdditionalProperties()).containsEntry("policy-owner", "platform");
    }

    @Test
    void editsV1Beta1PodSecurityPolicySpecNestedSecurityCollections() {
        PodSecurityPolicySpec spec = new PodSecurityPolicySpecBuilder()
                .addNewAllowedHostPath()
                    .withPathPrefix("/var/log")
                    .withReadOnly(true)
                .endAllowedHostPath()
                .addNewAllowedHostPath()
                    .withPathPrefix("/var/lib/app")
                    .withReadOnly(false)
                .endAllowedHostPath()
                .addNewHostPort()
                    .withMin(80)
                    .withMax(80)
                .endHostPort()
                .addNewHostPort()
                    .withMin(30000)
                    .withMax(32767)
                .endHostPort()
                .addNewAllowedCSIDriver()
                    .withName("csi.fast.example.com")
                .endAllowedCSIDriver()
                .addNewAllowedCSIDriver()
                    .withName("csi.slow.example.com")
                .endAllowedCSIDriver()
                .build();

        PodSecurityPolicySpec edited = new PodSecurityPolicySpecBuilder(spec)
                .editMatchingAllowedHostPath(hostPath -> "/var/lib/app".equals(hostPath.getPathPrefix()))
                    .withReadOnly(true)
                .endAllowedHostPath()
                .editMatchingHostPort(hostPort -> Integer.valueOf(30000).equals(hostPort.getMin()))
                    .withMax(31000)
                .endHostPort()
                .removeMatchingFromAllowedCSIDrivers(driver -> driver.getName().contains("slow"))
                .build();

        assertThat(spec.getAllowedHostPaths()).hasSize(2);
        assertThat(spec.getAllowedHostPaths().get(1).getReadOnly()).isFalse();
        assertThat(spec.getHostPorts().get(1).getMax()).isEqualTo(32767);
        assertThat(spec.getAllowedCSIDrivers()).hasSize(2);

        assertThat(edited.getAllowedHostPaths())
                .extracting(AllowedHostPath::getPathPrefix)
                .containsExactly("/var/log", "/var/lib/app");
        assertThat(edited.getAllowedHostPaths().get(1).getReadOnly()).isTrue();
        assertThat(edited.getHostPorts())
                .extracting(HostPortRange::getMin)
                .containsExactly(80, 30000);
        assertThat(edited.getHostPorts().get(1).getMax()).isEqualTo(31000);
        assertThat(edited.getAllowedCSIDrivers())
                .singleElement()
                .satisfies(driver -> assertThat(driver.getName()).isEqualTo("csi.fast.example.com"));
    }

    @Test
    void buildsAndEditsV1Beta1PodSecurityPolicyList() {
        PodSecurityPolicy restricted = new PodSecurityPolicyBuilder()
                .withApiVersion("policy/v1beta1")
                .withKind("PodSecurityPolicy")
                .withNewMetadata()
                    .withName("restricted")
                .endMetadata()
                .withNewSpec()
                    .withPrivileged(false)
                    .withVolumes("configMap", "secret")
                .endSpec()
                .build();

        PodSecurityPolicyList list = new PodSecurityPolicyListBuilder()
                .withApiVersion("policy/v1beta1")
                .withKind("PodSecurityPolicyList")
                .addToItems(restricted)
                .addNewItem()
                    .withApiVersion("policy/v1beta1")
                    .withKind("PodSecurityPolicy")
                    .withNewMetadata()
                        .withName("baseline")
                    .endMetadata()
                    .withNewSpec()
                        .withPrivileged(false)
                        .withVolumes("*")
                    .endSpec()
                .endItem()
                .build();

        PodSecurityPolicyList edited = new PodSecurityPolicyListBuilder(list)
                .editMatchingItem(item -> "baseline".equals(item.buildMetadata().getName()))
                    .editSpec()
                        .withReadOnlyRootFilesystem(true)
                    .endSpec()
                .endItem()
                .build();

        assertThat(edited).isInstanceOf(KubernetesResource.class);
        assertThat(edited.getItems()).hasSize(2);
        assertThat(edited.getItems().get(0).getMetadata().getName()).isEqualTo("restricted");
        assertThat(edited.getItems().get(1).getSpec().getVolumes()).containsExactly("*");
        assertThat(edited.getItems().get(1).getSpec().getReadOnlyRootFilesystem()).isTrue();
    }
}
