/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.Pluralize;
import io.fabric8.kubernetes.api.model.AuthInfo;
import io.fabric8.kubernetes.api.model.AuthInfoBuilder;
import io.fabric8.kubernetes.api.model.Cluster;
import io.fabric8.kubernetes.api.model.ClusterBuilder;
import io.fabric8.kubernetes.api.model.Config;
import io.fabric8.kubernetes.api.model.ConfigBuilder;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ConfigMapListBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.NamedAuthInfo;
import io.fabric8.kubernetes.api.model.NamedAuthInfoBuilder;
import io.fabric8.kubernetes.api.model.NamedCluster;
import io.fabric8.kubernetes.api.model.NamedClusterBuilder;
import io.fabric8.kubernetes.api.model.NamedContext;
import io.fabric8.kubernetes.api.model.NamedContextBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServiceListBuilder;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

public class Kubernetes_model_coreTest {
    @Test
    void podBuilderCreatesNestedSpecStatusAndEditableCopies() {
        Pod pod = new PodBuilder()
                .withNewMetadata()
                    .withName("web-0")
                    .withNamespace("production")
                    .addToLabels("app", "web")
                    .addToAnnotations("fabric8.io/generated-by", "test")
                    .addNewOwnerReference()
                        .withApiVersion("v1")
                        .withKind("ReplicationController")
                        .withName("web")
                        .withUid("owner-uid")
                        .withController(true)
                    .endOwnerReference()
                .endMetadata()
                .withNewSpec()
                    .withServiceAccountName("web")
                    .withRestartPolicy("Always")
                    .addNewImagePullSecret("registry-credentials")
                    .addToNodeSelector("kubernetes.io/os", "linux")
                    .addNewToleration()
                        .withKey("workload")
                        .withOperator("Equal")
                        .withValue("frontend")
                        .withEffect("NoSchedule")
                        .withTolerationSeconds(60L)
                    .endToleration()
                    .addNewVolume()
                        .withName("config")
                        .withNewConfigMap()
                            .withName("web-config")
                            .addNewItem()
                                .withKey("application.properties")
                                .withPath("application.properties")
                            .endItem()
                        .endConfigMap()
                    .endVolume()
                    .addNewVolume()
                        .withName("cache")
                        .withNewEmptyDir()
                            .withMedium("Memory")
                            .withSizeLimit(new Quantity("64Mi"))
                        .endEmptyDir()
                    .endVolume()
                    .addNewContainer()
                        .withName("app")
                        .withImage("example/web:latest")
                        .withImagePullPolicy("IfNotPresent")
                        .withCommand("/bin/sh", "-c")
                        .withArgs("run-web")
                        .addNewPort()
                            .withName("http")
                            .withContainerPort(8080)
                            .withProtocol("TCP")
                        .endPort()
                        .addNewEnv()
                            .withName("POD_NAME")
                            .withNewValueFrom()
                                .withNewFieldRef()
                                    .withFieldPath("metadata.name")
                                .endFieldRef()
                            .endValueFrom()
                        .endEnv()
                        .addNewVolumeMount()
                            .withName("config")
                            .withMountPath("/etc/web")
                            .withReadOnly(true)
                        .endVolumeMount()
                        .withNewResources()
                            .addToRequests("cpu", new Quantity("250m"))
                            .addToLimits("memory", new Quantity("256Mi"))
                        .endResources()
                        .withNewReadinessProbe()
                            .withInitialDelaySeconds(2)
                            .withPeriodSeconds(5)
                            .withNewHttpGet()
                                .withPath("/ready")
                                .withNewPort("http")
                                .withScheme("HTTP")
                            .endHttpGet()
                        .endReadinessProbe()
                    .endContainer()
                .endSpec()
                .withNewStatus()
                    .withPhase("Running")
                    .withPodIP("10.244.0.10")
                    .addNewCondition()
                        .withType("Ready")
                        .withStatus("True")
                        .withReason("ContainersReady")
                    .endCondition()
                    .addNewContainerStatus()
                        .withName("app")
                        .withReady(true)
                        .withRestartCount(0)
                        .withImage("example/web:latest")
                        .withImageID("sha256:abc")
                        .withNewState()
                            .withNewRunning()
                                .withStartedAt("2026-01-01T00:00:00Z")
                            .endRunning()
                        .endState()
                    .endContainerStatus()
                .endStatus()
                .addToAdditionalProperties("x-test", "pod")
                .build();

        assertThat(pod).isInstanceOf(HasMetadata.class);
        assertThat(pod.getApiVersion()).isEqualTo("v1");
        assertThat(pod.getKind()).isEqualTo("Pod");
        assertThat(pod.getMetadata().getOwnerReferences().get(0).getController()).isTrue();
        assertThat(pod.getSpec().getImagePullSecrets().get(0).getName()).isEqualTo("registry-credentials");
        assertThat(pod.getSpec().getNodeSelector()).containsEntry("kubernetes.io/os", "linux");
        assertThat(pod.getSpec().getTolerations().get(0).getTolerationSeconds()).isEqualTo(60L);
        assertThat(pod.getSpec().getVolumes()).extracting(volume -> volume.getName())
                .containsExactly("config", "cache");
        assertThat(pod.getSpec().getVolumes().get(0).getConfigMap().getItems().get(0).getPath())
                .isEqualTo("application.properties");
        assertThat(pod.getSpec().getContainers().get(0).getPorts().get(0).getContainerPort())
                .isEqualTo(8080);
        assertThat(pod.getSpec().getContainers().get(0).getEnv().get(0).getValueFrom().getFieldRef().getFieldPath())
                .isEqualTo("metadata.name");
        assertThat(pod.getSpec().getContainers().get(0).getReadinessProbe().getHttpGet().getPort().getStrVal())
                .isEqualTo("http");
        assertThat(pod.getStatus().getContainerStatuses().get(0).getState().getRunning().getStartedAt())
                .isEqualTo("2026-01-01T00:00:00Z");
        assertThat(pod.getAdditionalProperties()).containsEntry("x-test", "pod");

        Pod edited = pod.toBuilder()
                .editMetadata()
                    .addToLabels("track", "stable")
                .endMetadata()
                .editSpec()
                    .editFirstContainer()
                        .editFirstPort()
                            .withContainerPort(9090)
                        .endPort()
                    .endContainer()
                .endSpec()
                .editStatus()
                    .withPhase("Succeeded")
                .endStatus()
                .build();

        assertThat(edited).isNotEqualTo(pod);
        assertThat(edited.toBuilder().build()).isEqualTo(edited);
        assertThat(edited.getMetadata().getLabels()).containsEntry("track", "stable");
        assertThat(edited.getSpec().getContainers().get(0).getPorts().get(0).getContainerPort())
                .isEqualTo(9090);
        assertThat(edited.getStatus().getPhase()).isEqualTo("Succeeded");
        assertThat(pod.getStatus().getPhase()).isEqualTo("Running");
    }

    @Test
    void serviceAndListBuildersModelSelectorsPortsAndLoadBalancerStatus() {
        Service service = new ServiceBuilder()
                .withNewMetadata()
                    .withName("web")
                    .withNamespace("production")
                    .addToLabels("app", "web")
                .endMetadata()
                .withNewSpec()
                    .withType("LoadBalancer")
                    .withClusterIP("10.96.1.20")
                    .withClusterIPs("10.96.1.20")
                    .withIpFamilies("IPv4")
                    .withIpFamilyPolicy("SingleStack")
                    .withExternalTrafficPolicy("Local")
                    .withSessionAffinity("ClientIP")
                    .withNewSessionAffinityConfig()
                        .withNewClientIP()
                            .withTimeoutSeconds(600)
                        .endClientIP()
                    .endSessionAffinityConfig()
                    .addToSelector("app", "web")
                    .addNewPort()
                        .withName("http")
                        .withProtocol("TCP")
                        .withPort(80)
                        .withNewTargetPort("http")
                        .withNodePort(30080)
                    .endPort()
                    .addNewPort()
                        .withName("metrics")
                        .withProtocol("TCP")
                        .withPort(9100)
                        .withNewTargetPort(9100)
                    .endPort()
                .endSpec()
                .withNewStatus()
                    .withNewLoadBalancer()
                        .addNewIngress()
                            .withIp("192.0.2.10")
                            .withHostname("lb.example.test")
                            .addNewPort()
                                .withPort(80)
                                .withProtocol("TCP")
                            .endPort()
                        .endIngress()
                    .endLoadBalancer()
                .endStatus()
                .build();

        assertThat(service.getApiVersion()).isEqualTo("v1");
        assertThat(service.getKind()).isEqualTo("Service");
        assertThat(service.getSpec().getSelector()).containsEntry("app", "web");
        assertThat(service.getSpec().getSessionAffinityConfig().getClientIP().getTimeoutSeconds())
                .isEqualTo(600);
        assertThat(service.getSpec().getPorts().get(0).getTargetPort().getStrVal()).isEqualTo("http");
        assertThat(service.getSpec().getPorts().get(1).getTargetPort().getIntVal()).isEqualTo(9100);
        assertThat(service.getStatus().getLoadBalancer().getIngress().get(0).getPorts().get(0).getPort())
                .isEqualTo(80);

        Service edited = service.toBuilder()
                .editSpec()
                    .editMatchingPort(port -> "metrics".equals(port.getName()))
                        .withPort(9200)
                    .endPort()
                .endSpec()
                .build();

        assertThat(edited.getSpec().getPorts().get(1).getPort()).isEqualTo(9200);
        assertThat(service.getSpec().getPorts().get(1).getPort()).isEqualTo(9100);

        ServiceList services = new ServiceListBuilder()
                .withNewMetadata(null, null, "100", null)
                .withItems(service, edited)
                .build();

        assertThat(services.getApiVersion()).isEqualTo("v1");
        assertThat(services.getKind()).isEqualTo("ServiceList");
        assertThat(services.getItems()).hasSize(2);
        assertThat(services.getItems()).extracting(item -> item.getSpec().getPorts().get(1).getPort())
                .containsExactly(9100, 9200);
    }

    @Test
    void configMapSecretAndAggregateListsRetainMetadataAndData() {
        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                    .withName("web-config")
                    .withNamespace("production")
                    .addToLabels("app", "web")
                .endMetadata()
                .addToData("application.properties", "feature.enabled=true")
                .addToBinaryData("logo.png", "iVBORw0KGgo=")
                .withImmutable(true)
                .build();

        Secret secret = new SecretBuilder()
                .withNewMetadata()
                    .withName("registry-credentials")
                    .withNamespace("production")
                .endMetadata()
                .withType("kubernetes.io/dockerconfigjson")
                .addToStringData(".dockerconfigjson", "{\"auths\":{}}")
                .addToData("token", "YWJj")
                .withImmutable(false)
                .addToAdditionalProperties("managed", true)
                .build();

        assertThat(configMap.getKind()).isEqualTo("ConfigMap");
        assertThat(configMap.getData()).containsEntry("application.properties", "feature.enabled=true");
        assertThat(configMap.getBinaryData()).containsEntry("logo.png", "iVBORw0KGgo=");
        assertThat(configMap.getImmutable()).isTrue();
        assertThat(secret.getKind()).isEqualTo("Secret");
        assertThat(secret.getStringData()).containsEntry(".dockerconfigjson", "{\"auths\":{}}");
        assertThat(secret.getAdditionalProperties()).containsEntry("managed", true);

        ConfigMap editedConfigMap = configMap.toBuilder()
                .addToData("log.level", "debug")
                .build();
        ConfigMapList configMaps = new ConfigMapListBuilder()
                .withNewMetadata(null, null, "44", null)
                .withItems(configMap, editedConfigMap)
                .build();
        KubernetesList resources = new KubernetesListBuilder()
                .withItems(configMap, secret)
                .build();

        assertThat(editedConfigMap.getData()).containsEntry("log.level", "debug");
        assertThat(configMap.getData()).doesNotContainKey("log.level");
        assertThat(configMaps.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("web-config", "web-config");
        assertThat(resources.getItems()).hasSize(2);
        assertThat(resources.getItems()).allSatisfy(item -> assertThat(item).isInstanceOf(HasMetadata.class));
    }

    @Test
    void persistentVolumesClaimsNodesNamespacesAndStatusesUseCoreResourceModels() {
        PersistentVolume volume = new PersistentVolumeBuilder()
                .withNewMetadata()
                    .withName("pv-web")
                    .addToLabels("storage", "fast")
                .endMetadata()
                .withNewSpec()
                    .addToCapacity("storage", new Quantity("10Gi"))
                    .withAccessModes("ReadWriteOnce")
                    .withPersistentVolumeReclaimPolicy("Retain")
                    .withStorageClassName("fast")
                    .withVolumeMode("Filesystem")
                    .withNewHostPath()
                        .withPath("/var/lib/web")
                        .withType("DirectoryOrCreate")
                    .endHostPath()
                    .withNewClaimRef()
                        .withNamespace("production")
                        .withName("web-data")
                    .endClaimRef()
                .endSpec()
                .withNewStatus()
                    .withPhase("Available")
                    .withMessage("ready")
                .endStatus()
                .build();

        PersistentVolumeClaim claim = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                    .withName("web-data")
                    .withNamespace("production")
                .endMetadata()
                .withNewSpec()
                    .withAccessModes("ReadWriteOnce")
                    .withStorageClassName("fast")
                    .withVolumeName("pv-web")
                    .withNewResources()
                        .addToRequests("storage", new Quantity("10Gi"))
                    .endResources()
                    .withNewSelector()
                        .addToMatchLabels("storage", "fast")
                    .endSelector()
                .endSpec()
                .withNewStatus()
                    .withPhase("Bound")
                    .addToCapacity("storage", new Quantity("10Gi"))
                    .withAccessModes("ReadWriteOnce")
                .endStatus()
                .build();

        Node node = new NodeBuilder()
                .withNewMetadata()
                    .withName("worker-a")
                    .addToLabels("kubernetes.io/hostname", "worker-a")
                .endMetadata()
                .withNewSpec()
                    .withPodCIDR("10.244.1.0/24")
                    .withProviderID("kind://worker-a")
                    .addNewTaint()
                        .withKey("dedicated")
                        .withValue("web")
                        .withEffect("NoSchedule")
                    .endTaint()
                .endSpec()
                .withNewStatus()
                    .addToCapacity("cpu", new Quantity("4"))
                    .addToAllocatable("memory", new Quantity("8Gi"))
                    .addNewAddress()
                        .withType("InternalIP")
                        .withAddress("192.0.2.30")
                    .endAddress()
                    .addNewCondition()
                        .withType("Ready")
                        .withStatus("True")
                    .endCondition()
                    .withNewNodeInfo()
                        .withArchitecture("amd64")
                        .withOperatingSystem("linux")
                        .withKubeletVersion("v1.test")
                    .endNodeInfo()
                .endStatus()
                .build();

        Namespace namespace = new NamespaceBuilder()
                .withNewMetadata()
                    .withName("production")
                    .addToLabels("team", "platform")
                .endMetadata()
                .withNewStatus()
                    .withPhase("Active")
                .endStatus()
                .build();
        Status status = new StatusBuilder()
                .withStatus("Success")
                .withReason("Created")
                .withCode(201)
                .withMessage("resource created")
                .build();

        assertThat(volume.getSpec().getCapacity()).containsEntry("storage", new Quantity("10Gi"));
        assertThat(volume.getSpec().getHostPath().getPath()).isEqualTo("/var/lib/web");
        assertThat(volume.getSpec().getClaimRef().getName()).isEqualTo("web-data");
        assertThat(claim.getSpec().getResources().getRequests()).containsEntry("storage", new Quantity("10Gi"));
        assertThat(claim.getStatus().getPhase()).isEqualTo("Bound");
        assertThat(node.getSpec().getTaints().get(0).getEffect()).isEqualTo("NoSchedule");
        assertThat(node.getStatus().getAddresses().get(0).getAddress()).isEqualTo("192.0.2.30");
        assertThat(node.getStatus().getNodeInfo().getOperatingSystem()).isEqualTo("linux");
        assertThat(namespace.getStatus().getPhase()).isEqualTo("Active");
        assertThat(status.getCode()).isEqualTo(201);

        PersistentVolumeClaim resized = claim.toBuilder()
                .editSpec()
                    .editResources()
                        .addToRequests("storage", new Quantity("20Gi"))
                    .endResources()
                .endSpec()
                .build();

        assertThat(resized.getSpec().getResources().getRequests())
                .containsEntry("storage", new Quantity("20Gi"));
        assertThat(claim.getSpec().getResources().getRequests())
                .containsEntry("storage", new Quantity("10Gi"));
    }

    @Test
    void kubeConfigObjectsSupportNamedClustersContextsUsersAndExecAuth() {
        Cluster cluster = new ClusterBuilder()
                .withServer("https://cluster.example.test")
                .withCertificateAuthorityData("LS0tLS1DQS0tLS0=")
                .withInsecureSkipTlsVerify(false)
                .build();
        NamedCluster namedCluster = new NamedClusterBuilder()
                .withName("cluster-a")
                .withCluster(cluster)
                .build();
        NamedContext context = new NamedContextBuilder()
                .withName("cluster-a")
                .withNewContext()
                    .withCluster("cluster-a")
                    .withNamespace("production")
                    .withUser("developer")
                .endContext()
                .build();
        AuthInfo authInfo = new AuthInfoBuilder()
                .withToken("token-value")
                .withNewExec()
                    .withApiVersion("client.authentication.k8s.io/v1")
                    .withCommand("kubectl")
                    .withArgs("oidc-login", "get-token")
                    .addNewEnv()
                        .withName("KUBERNETES_EXEC_INFO")
                        .withValue("enabled")
                    .endEnv()
                    .withInteractiveMode("Never")
                .endExec()
                .build();
        NamedAuthInfo user = new NamedAuthInfoBuilder()
                .withName("developer")
                .withUser(authInfo)
                .build();

        Config config = new ConfigBuilder()
                .withApiVersion("v1")
                .withKind("Config")
                .withCurrentContext("cluster-a")
                .withClusters(namedCluster)
                .withContexts(context)
                .withUsers(user)
                .withNewPreferences()
                    .withColors(true)
                .endPreferences()
                .addNewExtension("integration-test", "fabric8")
                .build();

        assertThat(config).isInstanceOf(KubernetesResource.class);
        assertThat(config.getCurrentContext()).isEqualTo("cluster-a");
        assertThat(config.getClusters().get(0).getCluster().getServer())
                .isEqualTo("https://cluster.example.test");
        assertThat(config.getContexts().get(0).getContext().getNamespace()).isEqualTo("production");
        assertThat(config.getUsers().get(0).getUser().getExec().getCommand()).isEqualTo("kubectl");
        assertThat(config.getUsers().get(0).getUser().getExec().getEnv().get(0).getName())
                .isEqualTo("KUBERNETES_EXEC_INFO");
        assertThat(config.getPreferences().getColors()).isTrue();
        assertThat(config.getExtensions().get(0).getName()).isEqualTo("fabric8");

        Config switched = config.toBuilder()
                .editFirstContext()
                    .editContext()
                        .withNamespace("staging")
                    .endContext()
                .endContext()
                .build();

        assertThat(switched.getContexts().get(0).getContext().getNamespace()).isEqualTo("staging");
        assertThat(config.getContexts().get(0).getContext().getNamespace()).isEqualTo("production");
    }

    @Test
    void utilityTypesServiceLoaderAndPluralizationExposeCoreResourceMetadata() {
        Quantity memory = new Quantity("512Mi");
        Quantity doubledMemory = memory.multiply(2);
        IntOrString numericPort = new IntOrString(8443);
        IntOrString namedPort = new IntOrString("https");

        assertThat(memory.getAmount()).isEqualTo("512");
        assertThat(memory.getFormat()).isEqualTo("Mi");
        assertThat(doubledMemory.compareTo(new Quantity("1Gi"))).isEqualTo(0);
        assertThat(numericPort.getIntVal()).isEqualTo(8443);
        assertThat(namedPort.getStrVal()).isEqualTo("https");
        assertThat(Pluralize.toPlural("policy")).isEqualTo("policies");
        assertThat(Pluralize.toPlural("service")).isEqualTo("services");

        List<KubernetesResource> loadedResources = new ArrayList<>();
        ServiceLoader.load(KubernetesResource.class).forEach(loadedResources::add);

        assertThat(loadedResources).isNotEmpty();
        assertThat(loadedResources).anySatisfy(resource -> assertThat(resource).isInstanceOf(Pod.class));
        assertThat(loadedResources).anySatisfy(resource -> assertThat(resource).isInstanceOf(Service.class));
        assertThat(loadedResources).anySatisfy(resource -> assertThat(resource).isInstanceOf(ConfigMap.class));

        PodList pods = new PodListBuilder()
                .withNewMetadata(null, null, "55", null)
                .addNewItem()
                    .withNewMetadata()
                        .withName("web-0")
                    .endMetadata()
                    .withNewSpec()
                        .addNewContainer()
                            .withName("app")
                            .withImage("example/web:latest")
                        .endContainer()
                    .endSpec()
                .endItem()
                .build();

        assertThat(pods.getKind()).isEqualTo("PodList");
        assertThat(pods.getItems()).hasSize(1);
        assertThat(pods.getItems().get(0).getMetadata().getName()).isEqualTo("web-0");
    }
}
