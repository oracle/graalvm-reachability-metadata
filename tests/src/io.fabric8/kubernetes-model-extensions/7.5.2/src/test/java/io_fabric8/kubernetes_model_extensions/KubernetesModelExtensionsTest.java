/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_extensions;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.LoadBalancerStatusBuilder;
import io.fabric8.kubernetes.api.model.extensions.AllowedCSIDriver;
import io.fabric8.kubernetes.api.model.extensions.AllowedFlexVolume;
import io.fabric8.kubernetes.api.model.extensions.AllowedHostPath;
import io.fabric8.kubernetes.api.model.extensions.DaemonSet;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetList;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetListBuilder;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.extensions.DeploymentList;
import io.fabric8.kubernetes.api.model.extensions.DeploymentListBuilder;
import io.fabric8.kubernetes.api.model.extensions.HostPortRange;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.IDRange;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBackend;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressList;
import io.fabric8.kubernetes.api.model.extensions.IngressListBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressRule;
import io.fabric8.kubernetes.api.model.extensions.IngressTLS;
import io.fabric8.kubernetes.api.model.extensions.NetworkPolicy;
import io.fabric8.kubernetes.api.model.extensions.NetworkPolicyBuilder;
import io.fabric8.kubernetes.api.model.extensions.NetworkPolicyList;
import io.fabric8.kubernetes.api.model.extensions.NetworkPolicyListBuilder;
import io.fabric8.kubernetes.api.model.extensions.PodSecurityPolicy;
import io.fabric8.kubernetes.api.model.extensions.PodSecurityPolicyBuilder;
import io.fabric8.kubernetes.api.model.extensions.PodSecurityPolicyList;
import io.fabric8.kubernetes.api.model.extensions.PodSecurityPolicyListBuilder;
import io.fabric8.kubernetes.api.model.extensions.PodSecurityPolicySpec;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetList;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetListBuilder;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetSpec;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetSpecBuilder;
import io.fabric8.kubernetes.api.model.extensions.Scale;
import io.fabric8.kubernetes.api.model.extensions.ScaleBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesModelExtensionsTest {
    private static final String EXTENSIONS_API_VERSION = "extensions/v1beta1";

    @Test
    void createsAndEditsControllerResources() {
        Deployment deployment = new DeploymentBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("Deployment")
                .withNewMetadata()
                    .withName("web")
                    .withNamespace("default")
                    .addToLabels("app", "web")
                .endMetadata()
                .withNewSpec()
                    .withReplicas(3)
                    .withMinReadySeconds(5)
                    .withProgressDeadlineSeconds(60)
                    .withRevisionHistoryLimit(2)
                    .withNewSelector()
                        .addToMatchLabels("app", "web")
                    .endSelector()
                    .withNewStrategy()
                        .withType("RollingUpdate")
                        .withNewRollingUpdate()
                            .withNewMaxSurge(1)
                            .withNewMaxUnavailable(0)
                        .endRollingUpdate()
                    .endStrategy()
                    .withNewRollbackTo(7L)
                .endSpec()
                .withNewStatus()
                    .withObservedGeneration(10L)
                    .withReplicas(3)
                    .withReadyReplicas(2)
                    .withAvailableReplicas(2)
                    .addNewCondition()
                        .withType("Available")
                        .withStatus("True")
                        .withReason("MinimumReplicasAvailable")
                    .endCondition()
                .endStatus()
                .build();

        Deployment editedDeployment = deployment.toBuilder()
                .editSpec()
                    .withReplicas(4)
                .endSpec()
                .editStatus()
                    .withUpdatedReplicas(4)
                .endStatus()
                .build();

        ReplicaSet replicaSet = new ReplicaSetBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("ReplicaSet")
                .withNewMetadata()
                    .withName("web-rs")
                    .addToLabels("app", "web")
                .endMetadata()
                .withNewSpec()
                    .withReplicas(4)
                    .withNewSelector()
                        .addToMatchLabels("app", "web")
                    .endSelector()
                .endSpec()
                .withNewStatus()
                    .withReplicas(4)
                    .withReadyReplicas(4)
                    .addNewCondition()
                        .withType("ReplicaFailure")
                        .withStatus("False")
                    .endCondition()
                .endStatus()
                .build();

        DaemonSet daemonSet = new DaemonSetBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("DaemonSet")
                .withNewMetadata()
                    .withName("log-agent")
                    .addToLabels("app", "agent")
                .endMetadata()
                .withNewSpec()
                    .withMinReadySeconds(1)
                    .withTemplateGeneration(12L)
                    .withRevisionHistoryLimit(3)
                    .withNewSelector()
                        .addToMatchLabels("app", "agent")
                    .endSelector()
                    .withNewUpdateStrategy()
                        .withType("RollingUpdate")
                        .withNewRollingUpdate()
                            .withNewMaxUnavailable("25%")
                        .endRollingUpdate()
                    .endUpdateStrategy()
                .endSpec()
                .withNewStatus()
                    .withDesiredNumberScheduled(5)
                    .withCurrentNumberScheduled(5)
                    .withNumberReady(4)
                    .withNumberAvailable(4)
                    .addNewCondition()
                        .withType("Progressing")
                        .withStatus("True")
                    .endCondition()
                .endStatus()
                .build();

        assertThat(editedDeployment.getMetadata().getName()).isEqualTo("web");
        assertThat(editedDeployment.getSpec().getReplicas()).isEqualTo(4);
        assertThat(editedDeployment.getSpec().getRollbackTo().getRevision()).isEqualTo(7L);
        assertThat(editedDeployment.getStatus().getConditions()).hasSize(1);
        assertThat(editedDeployment.getStatus().getUpdatedReplicas()).isEqualTo(4);
        assertThat(replicaSet.getSpec().getSelector().getMatchLabels()).containsEntry("app", "web");
        assertThat(replicaSet.getStatus().getReadyReplicas()).isEqualTo(4);
        assertThat(daemonSet.getSpec().getUpdateStrategy().getRollingUpdate().getMaxUnavailable()).isNotNull();
        assertThat(daemonSet.getStatus().getNumberAvailable()).isEqualTo(4);
    }

    @Test
    void createsIngressWithDefaultBackendRulesAndTls() {
        Ingress ingress = new IngressBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("Ingress")
                .withNewMetadata()
                    .withName("public-web")
                    .withNamespace("default")
                    .addToAnnotations("nginx.ingress.kubernetes.io/rewrite-target", "/")
                .endMetadata()
                .withNewSpec()
                    .withNewBackend()
                        .withServiceName("fallback")
                        .withNewServicePort(8080)
                    .endBackend()
                    .addNewRule()
                        .withHost("example.test")
                        .withNewHttp()
                            .addNewPath()
                                .withPath("/api")
                                .withNewBackend()
                                    .withServiceName("api")
                                    .withNewServicePort("http")
                                .endBackend()
                            .endPath()
                            .addNewPath()
                                .withPath("/static")
                                .withNewBackend()
                                    .withServiceName("assets")
                                    .withNewServicePort(80)
                                .endBackend()
                            .endPath()
                        .endHttp()
                    .endRule()
                    .addNewTl()
                        .withHosts("example.test", "www.example.test")
                        .withSecretName("example-tls")
                    .endTl()
                .endSpec()
                .withNewStatus()
                    .withLoadBalancer(new LoadBalancerStatusBuilder()
                            .addNewIngress()
                                .withIp("203.0.113.10")
                            .endIngress()
                            .build())
                .endStatus()
                .build();

        IngressRule rule = ingress.getSpec().getRules().get(0);
        HTTPIngressPath apiPath = rule.getHttp().getPaths().get(0);
        IngressBackend defaultBackend = ingress.getSpec().getBackend();
        IngressTLS tls = ingress.getSpec().getTls().get(0);

        assertThat(defaultBackend.getServiceName()).isEqualTo("fallback");
        assertThat(rule.getHost()).isEqualTo("example.test");
        assertThat(apiPath.getPath()).isEqualTo("/api");
        assertThat(apiPath.getBackend().getServiceName()).isEqualTo("api");
        assertThat(tls.getHosts()).containsExactly("example.test", "www.example.test");
        assertThat(ingress.getStatus().getLoadBalancer().getIngress().get(0).getIp()).isEqualTo("203.0.113.10");
    }

    @Test
    void createsNetworkPolicyWithSelectorsPeersPortsAndLists() {
        NetworkPolicy policy = new NetworkPolicyBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("NetworkPolicy")
                .withNewMetadata()
                    .withName("restrict-web")
                    .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                    .withNewPodSelector()
                        .addToMatchLabels("app", "web")
                    .endPodSelector()
                    .withPolicyTypes("Ingress", "Egress")
                    .addNewIngress()
                        .addNewFrom()
                            .withNewNamespaceSelector()
                                .addToMatchLabels("team", "platform")
                            .endNamespaceSelector()
                            .withNewPodSelector()
                                .addToMatchLabels("role", "gateway")
                            .endPodSelector()
                        .endFrom()
                        .addNewPort()
                            .withProtocol("TCP")
                            .withNewPort(443)
                        .endPort()
                    .endIngress()
                    .addNewEgress()
                        .addNewTo()
                            .withNewIpBlock()
                                .withCidr("10.0.0.0/8")
                                .addToExcept("10.96.0.0/12")
                            .endIpBlock()
                        .endTo()
                        .addNewPort()
                            .withProtocol("UDP")
                            .withNewPort(53)
                        .endPort()
                    .endEgress()
                .endSpec()
                .build();

        NetworkPolicy editedPolicy = policy.edit()
                .editSpec()
                    .setToPolicyTypes(1, "Egress")
                    .editFirstIngress()
                        .editFirstPort()
                            .withProtocol("TCP")
                        .endPort()
                    .endIngress()
                .endSpec()
                .build();
        NetworkPolicyList list = new NetworkPolicyListBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("NetworkPolicyList")
                .addToItems(editedPolicy)
                .build();

        assertThat(editedPolicy.getSpec().getPodSelector().getMatchLabels()).containsEntry("app", "web");
        assertThat(editedPolicy.getSpec().getPolicyTypes()).containsExactly("Ingress", "Egress");
        assertThat(editedPolicy.getSpec().getIngress()).hasSize(1);
        assertThat(editedPolicy.getSpec().getIngress().get(0).getFrom().get(0)
                .getNamespaceSelector().getMatchLabels()).containsEntry("team", "platform");
        assertThat(editedPolicy.getSpec().getIngress().get(0).getPorts().get(0).getProtocol()).isEqualTo("TCP");
        assertThat(editedPolicy.getSpec().getEgress().get(0).getTo().get(0).getIpBlock().getCidr())
                .isEqualTo("10.0.0.0/8");
        assertThat(list.getItems()).containsExactly(editedPolicy);
    }

    @Test
    void createsControllerSpecWithPodTemplate() {
        ReplicaSetSpec spec = new ReplicaSetSpecBuilder()
                .withReplicas(2)
                .withNewSelector()
                    .addToMatchLabels("app", "templated-web")
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToLabels("app", "templated-web")
                    .endMetadata()
                    .withNewSpec()
                        .addNewContainer()
                            .withName("web")
                            .withImage("nginx")
                            .addNewPort()
                                .withName("http")
                                .withContainerPort(8080)
                            .endPort()
                            .addNewEnv()
                                .withName("APP_MODE")
                                .withValue("production")
                            .endEnv()
                        .endContainer()
                    .endSpec()
                .endTemplate()
                .build();

        ReplicaSetSpec editedSpec = new ReplicaSetSpecBuilder(spec)
                .editTemplate()
                    .editSpec()
                        .editFirstContainer()
                            .withImage("nginx:stable")
                        .endContainer()
                        .addNewImagePullSecret()
                            .withName("registry-secret")
                        .endImagePullSecret()
                    .endSpec()
                .endTemplate()
                .build();
        Container container = editedSpec.getTemplate().getSpec().getContainers().get(0);

        assertThat(editedSpec.getReplicas()).isEqualTo(2);
        assertThat(editedSpec.getSelector().getMatchLabels()).containsEntry("app", "templated-web");
        assertThat(editedSpec.getTemplate().getMetadata().getLabels()).containsEntry("app", "templated-web");
        assertThat(container.getName()).isEqualTo("web");
        assertThat(container.getImage()).isEqualTo("nginx:stable");
        assertThat(container.getPorts().get(0).getName()).isEqualTo("http");
        assertThat(container.getPorts().get(0).getContainerPort()).isEqualTo(8080);
        assertThat(container.getEnv().get(0).getName()).isEqualTo("APP_MODE");
        assertThat(container.getEnv().get(0).getValue()).isEqualTo("production");
        assertThat(editedSpec.getTemplate().getSpec().getImagePullSecrets())
                .extracting(secret -> secret.getName())
                .containsExactly("registry-secret");
    }

    @Test
    void createsPodSecurityPolicyWithStrategyOptions() {
        PodSecurityPolicy podSecurityPolicy = new PodSecurityPolicyBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("PodSecurityPolicy")
                .withNewMetadata()
                    .withName("restricted")
                .endMetadata()
                .withNewSpec()
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
                    .withVolumes("configMap", "secret", "emptyDir")
                    .withAllowedProcMountTypes("Default")
                    .withAllowedUnsafeSysctls("kernel.shm_rmid_forced")
                    .withForbiddenSysctls("net.*")
                    .addNewAllowedCSIDriver("secrets-store.csi.k8s.io")
                    .addNewAllowedFlexVolume("example.com/flex")
                    .addNewAllowedHostPath("/var/log", true)
                    .addNewHostPort(443, 80)
                    .withNewFsGroup()
                        .withRule("MustRunAs")
                        .addNewRange(2000L, 1000L)
                    .endFsGroup()
                    .withNewRunAsUser()
                        .withRule("MustRunAs")
                        .addNewRange(2000L, 1000L)
                    .endRunAsUser()
                    .withNewRunAsGroup()
                        .withRule("MustRunAs")
                        .addNewRange(4000L, 3000L)
                    .endRunAsGroup()
                    .withNewSupplementalGroups()
                        .withRule("RunAsAny")
                        .addNewRange(6000L, 5000L)
                    .endSupplementalGroups()
                    .withNewRuntimeClass()
                        .withDefaultRuntimeClassName("runc")
                        .withAllowedRuntimeClassNames("runc", "gvisor")
                    .endRuntimeClass()
                    .withNewSeLinux()
                        .withRule("RunAsAny")
                        .withNewSeLinuxOptions("system_u", "system_r", "container_t", "s0:c123,c456")
                    .endSeLinux()
                .endSpec()
                .build();

        PodSecurityPolicySpec spec = podSecurityPolicy.getSpec();
        AllowedCSIDriver csiDriver = spec.getAllowedCSIDrivers().get(0);
        AllowedFlexVolume flexVolume = spec.getAllowedFlexVolumes().get(0);
        AllowedHostPath hostPath = spec.getAllowedHostPaths().get(0);
        HostPortRange hostPort = spec.getHostPorts().get(0);
        IDRange userRange = spec.getRunAsUser().getRanges().get(0);

        assertThat(spec.getPrivileged()).isFalse();
        assertThat(spec.getReadOnlyRootFilesystem()).isTrue();
        assertThat(spec.getVolumes()).containsExactly("configMap", "secret", "emptyDir");
        assertThat(csiDriver.getName()).isEqualTo("secrets-store.csi.k8s.io");
        assertThat(flexVolume.getDriver()).isEqualTo("example.com/flex");
        assertThat(hostPath.getPathPrefix()).isEqualTo("/var/log");
        assertThat(hostPath.getReadOnly()).isTrue();
        assertThat(hostPort.getMin()).isEqualTo(80);
        assertThat(hostPort.getMax()).isEqualTo(443);
        assertThat(userRange.getMin()).isEqualTo(1000L);
        assertThat(userRange.getMax()).isEqualTo(2000L);
        assertThat(spec.getRuntimeClass().getAllowedRuntimeClassNames()).containsExactly("runc", "gvisor");
        assertThat(spec.getSeLinux().getSeLinuxOptions().getType()).isEqualTo("container_t");
    }

    @Test
    void createsScaleAndResourceListsWithAdditionalProperties() {
        Scale scale = new ScaleBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("Scale")
                .withNewMetadata()
                    .withName("web")
                    .withNamespace("default")
                .endMetadata()
                .withNewSpec(5)
                .withNewStatus()
                    .withReplicas(4)
                    .addToSelector("app", "web")
                    .withTargetSelector("app=web")
                .endStatus()
                .addToAdditionalProperties("source", "test")
                .build();

        Scale editedScale = scale.toBuilder()
                .editSpec()
                    .withReplicas(6)
                .endSpec()
                .removeFromAdditionalProperties("source")
                .build();
        DeploymentList deployments = new DeploymentListBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("DeploymentList")
                .addNewItem()
                    .withNewMetadata()
                        .withName("web")
                    .endMetadata()
                .endItem()
                .build();
        ReplicaSetList replicaSets = new ReplicaSetListBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("ReplicaSetList")
                .addNewItem()
                    .withNewMetadata()
                        .withName("web-rs")
                    .endMetadata()
                .endItem()
                .build();
        DaemonSetList daemonSets = new DaemonSetListBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("DaemonSetList")
                .addNewItem()
                    .withNewMetadata()
                        .withName("agent")
                    .endMetadata()
                .endItem()
                .build();
        IngressList ingresses = new IngressListBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("IngressList")
                .addNewItem()
                    .withNewMetadata()
                        .withName("public-web")
                    .endMetadata()
                .endItem()
                .build();
        PodSecurityPolicyList podSecurityPolicies = new PodSecurityPolicyListBuilder()
                .withApiVersion(EXTENSIONS_API_VERSION)
                .withKind("PodSecurityPolicyList")
                .addNewItem()
                    .withNewMetadata()
                        .withName("restricted")
                    .endMetadata()
                .endItem()
                .build();

        assertThat(editedScale.getSpec().getReplicas()).isEqualTo(6);
        assertThat(editedScale.getStatus().getSelector()).containsEntry("app", "web");
        assertThat(editedScale.getStatus().getTargetSelector()).isEqualTo("app=web");
        assertThat(editedScale.getAdditionalProperties()).doesNotContainKey("source");
        assertThat(deployments.getItems()).extracting(item -> item.getMetadata().getName()).containsExactly("web");
        assertThat(replicaSets.getItems()).extracting(item -> item.getMetadata().getName()).containsExactly("web-rs");
        assertThat(daemonSets.getItems()).extracting(item -> item.getMetadata().getName()).containsExactly("agent");
        assertThat(ingresses.getItems()).extracting(item -> item.getMetadata().getName()).containsExactly("public-web");
        assertThat(podSecurityPolicies.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("restricted");
    }
}
