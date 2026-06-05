/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_networking;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.TypedLocalObjectReference;
import io.fabric8.kubernetes.api.model.networking.v1.IPAddress;
import io.fabric8.kubernetes.api.model.networking.v1.IPAddressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressClass;
import io.fabric8.kubernetes.api.model.networking.v1.IngressClassBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressClassList;
import io.fabric8.kubernetes.api.model.networking.v1.IngressClassListBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressList;
import io.fabric8.kubernetes.api.model.networking.v1.IngressListBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyList;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyListBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceCIDR;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceCIDRBuilder;
import io.fabric8.kubernetes.api.model.networking.v1beta1.IngressBackend;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesModelNetworkingTest {
    @Test
    void ingressBuilderCreatesRulesTlsStatusAndEditableCopies() {
        Ingress ingress = new IngressBuilder()
                .withNewMetadata()
                    .withName("web")
                    .withNamespace("production")
                    .addToLabels("app", "frontend")
                    .addToAnnotations("kubernetes.io/ingress.class", "nginx")
                .endMetadata()
                .withNewSpec()
                    .withIngressClassName("nginx")
                    .withNewDefaultBackend()
                        .withNewService()
                            .withName("fallback-service")
                            .withNewPort()
                                .withNumber(80)
                            .endPort()
                        .endService()
                    .endDefaultBackend()
                    .addNewRule()
                        .withHost("www.example.test")
                        .withNewHttp()
                            .addNewPath()
                                .withPath("/api")
                                .withPathType("Prefix")
                                .withNewBackend()
                                    .withNewService()
                                        .withName("api-service")
                                        .withNewPort()
                                            .withName("http")
                                        .endPort()
                                    .endService()
                                .endBackend()
                            .endPath()
                        .endHttp()
                    .endRule()
                    .addNewTl()
                        .addToHosts("www.example.test")
                        .withSecretName("example-tls")
                    .endTl()
                .endSpec()
                .withNewStatus()
                    .withNewLoadBalancer()
                        .addNewIngress()
                            .withHostname("lb.example.test")
                            .addNewPort()
                                .withPort(443)
                                .withProtocol("TCP")
                            .endPort()
                        .endIngress()
                    .endLoadBalancer()
                .endStatus()
                .addToAdditionalProperties("x-test", "ingress")
                .build();

        assertThat(ingress.getApiVersion()).isEqualTo("networking.k8s.io/v1");
        assertThat(ingress.getKind()).isEqualTo("Ingress");
        assertThat(ingress.getMetadata().getLabels()).containsEntry("app", "frontend");
        assertThat(ingress.getSpec().getIngressClassName()).isEqualTo("nginx");
        assertThat(ingress.getSpec().getDefaultBackend().getService().getPort().getNumber()).isEqualTo(80);
        assertThat(ingress.getSpec().getRules()).hasSize(1);
        assertThat(ingress.getSpec().getRules().get(0).getHost()).isEqualTo("www.example.test");
        assertThat(ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService().getName())
                .isEqualTo("api-service");
        assertThat(ingress.getSpec().getTls().get(0).getHosts()).containsExactly("www.example.test");
        assertThat(ingress.getStatus().getLoadBalancer().getIngress().get(0).getPorts().get(0).getPort())
                .isEqualTo(443);
        assertThat(ingress.getAdditionalProperties()).containsEntry("x-test", "ingress");

        Ingress edited = ingress.toBuilder()
                .editMetadata()
                    .addToAnnotations("edited", "true")
                .endMetadata()
                .editSpec()
                    .editFirstRule()
                        .withHost("api.example.test")
                    .endRule()
                .endSpec()
                .build();

        assertThat(edited).isNotEqualTo(ingress);
        assertThat(edited.getMetadata().getAnnotations()).containsEntry("edited", "true");
        assertThat(edited.getSpec().getRules().get(0).getHost()).isEqualTo("api.example.test");
        assertThat(ingress.getSpec().getRules().get(0).getHost()).isEqualTo("www.example.test");

        IngressList list = new IngressListBuilder()
                .withNewMetadata(null, null, "42", null)
                .withItems(ingress, edited)
                .build();

        assertThat(list.getApiVersion()).isEqualTo("networking.k8s.io/v1");
        assertThat(list.getKind()).isEqualTo("IngressList");
        assertThat(list.getItems()).extracting(item -> item.getMetadata().getName()).containsExactly("web", "web");
    }

    @Test
    void networkPolicyBuilderModelsIngressAndEgressSelectors() {
        NetworkPolicy policy = new NetworkPolicyBuilder()
                .withNewMetadata()
                    .withName("database-policy")
                    .withNamespace("production")
                    .addToLabels("tier", "database")
                .endMetadata()
                .withNewSpec()
                    .withNewPodSelector()
                        .addToMatchLabels("role", "database")
                    .endPodSelector()
                    .withPolicyTypes("Ingress", "Egress")
                    .addNewIngress()
                        .addNewFrom()
                            .withNewNamespaceSelector()
                                .addToMatchLabels("team", "platform")
                            .endNamespaceSelector()
                            .withNewPodSelector()
                                .addToMatchLabels("role", "api")
                            .endPodSelector()
                        .endFrom()
                        .addNewPort()
                            .withProtocol("TCP")
                            .withNewPort(5432)
                            .withEndPort(5432)
                        .endPort()
                    .endIngress()
                    .addNewEgress()
                        .addNewTo()
                            .withNewIpBlock()
                                .withCidr("10.96.0.0/12")
                                .addToExcept("10.96.0.10/32")
                            .endIpBlock()
                        .endTo()
                        .addNewPort()
                            .withProtocol("UDP")
                            .withNewPort(53)
                        .endPort()
                    .endEgress()
                .endSpec()
                .build();

        assertThat(policy.getApiVersion()).isEqualTo("networking.k8s.io/v1");
        assertThat(policy.getKind()).isEqualTo("NetworkPolicy");
        assertThat(policy.getSpec().getPodSelector().getMatchLabels()).containsEntry("role", "database");
        assertThat(policy.getSpec().getPolicyTypes()).containsExactly("Ingress", "Egress");
        assertThat(policy.getSpec().getIngress().get(0).getFrom().get(0).getNamespaceSelector().getMatchLabels())
                .containsEntry("team", "platform");
        assertThat(policy.getSpec().getIngress().get(0).getPorts().get(0).getPort().getIntVal()).isEqualTo(5432);
        assertThat(policy.getSpec().getIngress().get(0).getPorts().get(0).getEndPort()).isEqualTo(5432);
        assertThat(policy.getSpec().getEgress().get(0).getTo().get(0).getIpBlock().getCidr()).isEqualTo("10.96.0.0/12");
        assertThat(policy.getSpec().getEgress().get(0).getTo().get(0).getIpBlock().getExcept())
                .containsExactly("10.96.0.10/32");

        NetworkPolicy tightened = policy.toBuilder()
                .editSpec()
                    .editFirstEgress()
                        .editFirstPort()
                            .withNewPort(5353)
                        .endPort()
                    .endEgress()
                .endSpec()
                .build();

        assertThat(tightened.getSpec().getEgress().get(0).getPorts().get(0).getPort().getIntVal()).isEqualTo(5353);
        assertThat(policy.getSpec().getEgress().get(0).getPorts().get(0).getPort().getIntVal()).isEqualTo(53);

        NetworkPolicyList list = new NetworkPolicyListBuilder()
                .withNewMetadata("next-page", null, null, null)
                .withItems(policy, tightened)
                .build();

        assertThat(list.getKind()).isEqualTo("NetworkPolicyList");
        assertThat(list.getItems()).hasSize(2);
    }

    @Test
    void addressAndServiceCidrResourcesPreserveParentReferencesStatusAndAdditionalProperties() {
        IPAddress address = new IPAddressBuilder()
                .withNewMetadata()
                    .withName("10.96.0.10")
                .endMetadata()
                .withNewSpec()
                    .withNewParentRef("", "kubernetes", "default", "services")
                .endSpec()
                .addToAdditionalProperties("allocation", "static")
                .build();

        assertThat(address.getApiVersion()).isEqualTo("networking.k8s.io/v1");
        assertThat(address.getKind()).isEqualTo("IPAddress");
        assertThat(address.getSpec().getParentRef().getName()).isEqualTo("kubernetes");
        assertThat(address.getSpec().getParentRef().getNamespace()).isEqualTo("default");
        assertThat(address.getSpec().getParentRef().getResource()).isEqualTo("services");
        assertThat(address.getAdditionalProperties()).containsEntry("allocation", "static");

        Condition ready = new ConditionBuilder()
                .withType("Ready")
                .withStatus("True")
                .withReason("CIDRAllocated")
                .withMessage("Cluster service addresses are available")
                .withObservedGeneration(7L)
                .build();
        ServiceCIDR serviceCIDR = new ServiceCIDRBuilder()
                .withNewMetadata()
                    .withName("cluster-services")
                .endMetadata()
                .withNewSpec()
                    .addToCidrs("10.96.0.0/12", "fd00:10:96::/112")
                .endSpec()
                .withNewStatus()
                    .addToConditions(ready)
                .endStatus()
                .build();

        assertThat(serviceCIDR.getApiVersion()).isEqualTo("networking.k8s.io/v1");
        assertThat(serviceCIDR.getKind()).isEqualTo("ServiceCIDR");
        assertThat(serviceCIDR.getSpec().getCidrs()).containsExactly("10.96.0.0/12", "fd00:10:96::/112");
        assertThat(serviceCIDR.getStatus().getConditions()).containsExactly(ready);
        ServiceCIDR expandedServiceCIDR = serviceCIDR.toBuilder()
                .editSpec()
                    .addToCidrs("172.30.0.0/16")
                .endSpec()
                .build();
        assertThat(expandedServiceCIDR.getSpec().getCidrs())
                .containsExactly("10.96.0.0/12", "fd00:10:96::/112", "172.30.0.0/16");
    }

    @Test
    void ingressClassBuildersSupportParametersListsAndAdditionalProperties() {
        IngressClass ingressClass = new IngressClassBuilder()
                .withNewMetadata()
                    .withName("internal")
                    .addToAnnotations("ingressclass.kubernetes.io/is-default-class", "false")
                .endMetadata()
                .withNewSpec()
                    .withController("example.test/internal-controller")
                    .withNewParameters(
                            "networking.example.test",
                            "IngressParameters",
                            "internal-defaults",
                            "platform",
                            "Namespace")
                .endSpec()
                .addToAdditionalProperties("owner", "platform")
                .build();

        assertThat(ingressClass.getApiVersion()).isEqualTo("networking.k8s.io/v1");
        assertThat(ingressClass.getKind()).isEqualTo("IngressClass");
        assertThat(ingressClass.getSpec().getController()).isEqualTo("example.test/internal-controller");
        assertThat(ingressClass.getSpec().getParameters().getApiGroup()).isEqualTo("networking.example.test");
        assertThat(ingressClass.getSpec().getParameters().getKind()).isEqualTo("IngressParameters");
        assertThat(ingressClass.getSpec().getParameters().getNamespace()).isEqualTo("platform");
        assertThat(ingressClass.getAdditionalProperties()).containsEntry("owner", "platform");

        IngressClass external = ingressClass.toBuilder()
                .editMetadata()
                    .withName("external")
                .endMetadata()
                .editSpec()
                    .withController("example.test/external-controller")
                .endSpec()
                .removeFromAdditionalProperties("owner")
                .build();
        IngressClassList list = new IngressClassListBuilder()
                .withNewMetadata(null, 2L, null, null)
                .withItems(ingressClass, external)
                .build();

        assertThat(external.getAdditionalProperties()).doesNotContainKey("owner");
        assertThat(list.getApiVersion()).isEqualTo("networking.k8s.io/v1");
        assertThat(list.getKind()).isEqualTo("IngressClassList");
        assertThat(list.getItems())
                .extracting(item -> item.getMetadata().getName())
                .containsExactly("internal", "external");
    }

    @Test
    void v1beta1IngressBuilderSupportsLegacyServiceAndResourceBackends() {
        io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress ingress =
                new io.fabric8.kubernetes.api.model.networking.v1beta1.IngressBuilder()
                .withNewMetadata()
                    .withName("beta-ingress")
                    .withNamespace("production")
                .endMetadata()
                .withNewSpec()
                    .withIngressClassName("beta-controller")
                    .withNewBackend()
                        .withServiceName("legacy-default-service")
                        .withNewServicePort(8080)
                    .endBackend()
                    .addNewRule()
                        .withHost("beta.example.test")
                        .withNewHttp()
                            .addNewPath()
                                .withPath("/assets")
                                .withPathType("ImplementationSpecific")
                                .withNewBackend()
                                    .withNewResource("storage.example.test", "StorageBucket", "static-assets")
                                .endBackend()
                            .endPath()
                        .endHttp()
                    .endRule()
                    .addNewTl()
                        .addToHosts("beta.example.test")
                        .withSecretName("beta-tls")
                    .endTl()
                .endSpec()
                .addToAdditionalProperties("networking-tier", "beta")
                .build();

        assertThat(ingress.getApiVersion()).isEqualTo("networking.k8s.io/v1beta1");
        assertThat(ingress.getKind()).isEqualTo("Ingress");
        assertThat(ingress.getSpec().getIngressClassName()).isEqualTo("beta-controller");
        assertThat(ingress.getSpec().getBackend().getServiceName()).isEqualTo("legacy-default-service");
        assertThat(ingress.getSpec().getBackend().getServicePort().getIntVal()).isEqualTo(8080);
        IngressBackend resourceBackend = ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend();
        TypedLocalObjectReference resource = resourceBackend.getResource();
        assertThat(resource.getApiGroup()).isEqualTo("storage.example.test");
        assertThat(resource.getKind()).isEqualTo("StorageBucket");
        assertThat(resource.getName()).isEqualTo("static-assets");
        assertThat(ingress.getSpec().getTls().get(0).getHosts()).containsExactly("beta.example.test");
        assertThat(ingress.getAdditionalProperties()).containsEntry("networking-tier", "beta");

        io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress edited = ingress.toBuilder()
                .editSpec()
                    .editBackend()
                        .withServiceName("legacy-edited-service")
                    .endBackend()
                .endSpec()
                .build();

        assertThat(edited.getSpec().getBackend().getServiceName()).isEqualTo("legacy-edited-service");
        assertThat(ingress.getSpec().getBackend().getServiceName()).isEqualTo("legacy-default-service");
    }

    @Test
    void serviceLoaderDiscoversNetworkingResources() {
        List<String> discoveredApiKinds = new ArrayList<>();
        for (KubernetesResource resource : ServiceLoader.load(KubernetesResource.class)) {
            if (resource instanceof HasMetadata metadata) {
                String apiVersion = metadata.getApiVersion();
                if (apiVersion != null && apiVersion.startsWith("networking.k8s.io/")) {
                    discoveredApiKinds.add(apiVersion + "/" + metadata.getKind());
                }
            }
        }

        assertThat(discoveredApiKinds)
                .contains("networking.k8s.io/v1/Ingress")
                .contains("networking.k8s.io/v1/IngressClass")
                .contains("networking.k8s.io/v1/IPAddress")
                .contains("networking.k8s.io/v1/NetworkPolicy")
                .contains("networking.k8s.io/v1/ServiceCIDR")
                .contains("networking.k8s.io/v1beta1/Ingress")
                .contains("networking.k8s.io/v1beta1/ServiceCIDR");
    }
}
