/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_discovery;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ListMeta;
import io.fabric8.kubernetes.api.model.ListMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.Endpoint;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointConditions;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointConditionsBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointHints;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointHintsBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointPort;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceList;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceListBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.ForNode;
import io.fabric8.kubernetes.api.model.discovery.v1.ForNodeBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.ForZone;
import io.fabric8.kubernetes.api.model.discovery.v1.ForZoneBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Kubernetes_model_discoveryTest {
    @Test
    void endpointSliceBuilderCreatesV1SliceWithEndpointsPortsHintsAndMetadata() {
        EndpointSlice slice = new EndpointSliceBuilder()
                .withAddressType("IPv4")
                .withNewMetadata()
                    .withName("web-slice-abc")
                    .withNamespace("production")
                    .addToLabels("kubernetes.io/service-name", "web")
                    .addToAnnotations("endpointslice.kubernetes.io/managed-by", "unit-test")
                .endMetadata()
                .addNewEndpoint()
                    .withAddresses("10.0.0.10")
                    .withHostname("web-0")
                    .withNodeName("node-a")
                    .withZone("us-east-1a")
                    .withNewConditions(true, true, false)
                    .withNewHints()
                        .addNewForNode("node-a")
                        .addNewForZone("us-east-1a")
                    .endHints()
                    .withNewTargetRef()
                        .withApiVersion("v1")
                        .withKind("Pod")
                        .withName("web-0")
                        .withNamespace("production")
                        .withUid("pod-uid-0")
                    .endTargetRef()
                    .addToAdditionalProperties("endpoint-source", "controller")
                .endEndpoint()
                .addNewEndpoint()
                    .withAddresses("10.0.0.11")
                    .withHostname("web-1")
                    .withNodeName("node-b")
                    .withZone("us-east-1b")
                    .withNewConditions(true, false, true)
                    .withNewHints()
                        .addNewForNode("node-b")
                        .addNewForZone("us-east-1b")
                    .endHints()
                .endEndpoint()
                .addNewPort("kubernetes.io/h2c", "http", 8080, "TCP")
                .addNewPort(null, "metrics", 9090, "TCP")
                .addToAdditionalProperties("discovery-note", Map.of("tested", true))
                .build();

        assertThat(slice).isInstanceOf(HasMetadata.class);
        assertThat(slice.getApiVersion()).isEqualTo("discovery.k8s.io/v1");
        assertThat(slice.getKind()).isEqualTo("EndpointSlice");
        assertThat(slice.getAddressType()).isEqualTo("IPv4");
        assertThat(slice.getMetadata().getLabels()).containsEntry("kubernetes.io/service-name", "web");
        assertThat(slice.getMetadata().getAnnotations())
                .containsEntry("endpointslice.kubernetes.io/managed-by", "unit-test");
        assertThat(slice.getEndpoints()).hasSize(2);
        assertThat(slice.getEndpoints().get(0).getAddresses()).containsExactly("10.0.0.10");
        assertThat(slice.getEndpoints().get(0).getConditions().getReady()).isTrue();
        assertThat(slice.getEndpoints().get(0).getConditions().getServing()).isTrue();
        assertThat(slice.getEndpoints().get(0).getConditions().getTerminating()).isFalse();
        assertThat(slice.getEndpoints().get(0).getHints().getForNodes()).extracting(ForNode::getName)
                .containsExactly("node-a");
        assertThat(slice.getEndpoints().get(0).getHints().getForZones()).extracting(ForZone::getName)
                .containsExactly("us-east-1a");
        assertThat(slice.getEndpoints().get(0).getTargetRef().getKind()).isEqualTo("Pod");
        assertThat(slice.getEndpoints().get(0).getTargetRef().getUid()).isEqualTo("pod-uid-0");
        assertThat(slice.getEndpoints().get(0).getAdditionalProperties()).containsEntry("endpoint-source", "controller");
        assertThat(slice.getPorts()).extracting(EndpointPort::getName).containsExactly("http", "metrics");
        assertThat(slice.getPorts().get(0).getAppProtocol()).isEqualTo("kubernetes.io/h2c");
        assertThat(slice.getPorts().get(0).getPort()).isEqualTo(8080);
        assertThat(slice.getAdditionalProperties()).containsEntry("discovery-note", Map.of("tested", true));

        EndpointSlice edited = slice.toBuilder()
                .editMetadata()
                    .addToAnnotations("edited", "true")
                .endMetadata()
                .editMatchingEndpoint(endpoint -> "web-1".equals(endpoint.getHostname()))
                    .editConditions()
                        .withServing(true)
                        .withTerminating(false)
                    .endConditions()
                    .editHints()
                        .addNewForNode("node-c")
                    .endHints()
                .endEndpoint()
                .editMatchingPort(port -> "metrics".equals(port.getName()))
                    .withPort(9100)
                .endPort()
                .removeFromAdditionalProperties("discovery-note")
                .build();

        assertThat(edited.getMetadata().getAnnotations()).containsEntry("edited", "true");
        assertThat(edited.getEndpoints().get(1).getConditions().getServing()).isTrue();
        assertThat(edited.getEndpoints().get(1).getConditions().getTerminating()).isFalse();
        assertThat(edited.getEndpoints().get(1).getHints().getForNodes()).extracting(ForNode::getName)
                .containsExactly("node-b", "node-c");
        assertThat(edited.getPorts().get(1).getPort()).isEqualTo(9100);
        assertThat(edited.getAdditionalProperties()).doesNotContainKey("discovery-note");
        assertThat(slice.getEndpoints().get(1).getConditions().getServing()).isFalse();
        assertThat(slice.getPorts().get(1).getPort()).isEqualTo(9090);
    }

    @Test
    void endpointSliceListSupportsNestedItemsPredicateMatchingRemovalAndCopies() {
        ObjectMeta metadata = new ObjectMetaBuilder()
                .withName("api-slice")
                .withNamespace("default")
                .addToLabels("kubernetes.io/service-name", "api")
                .build();
        Endpoint apiEndpoint = new EndpointBuilder()
                .withAddresses("10.1.0.20")
                .withHostname("api-0")
                .withNodeName("node-a")
                .withZone("us-west-2a")
                .withNewConditions(true, true, false)
                .build();
        EndpointSlice apiSlice = new EndpointSlice(
                "IPv4",
                "discovery.k8s.io/v1",
                List.of(apiEndpoint),
                "EndpointSlice",
                metadata,
                List.of(new EndpointPort("grpc", "grpc", 8443, "TCP")));
        apiSlice.setAdditionalProperty("constructed", true);
        EndpointSlice copied = new EndpointSliceBuilder(apiSlice).build();

        ListMeta listMetadata = new ListMetaBuilder()
                .withContinue("next-page")
                .withRemainingItemCount(1L)
                .withResourceVersion("42")
                .build();
        EndpointSliceList list = new EndpointSliceListBuilder()
                .withMetadata(listMetadata)
                .addToItems(copied)
                .addNewItem()
                    .withAddressType("IPv6")
                    .withNewMetadata()
                        .withName("metrics-slice")
                        .withNamespace("default")
                    .endMetadata()
                    .addNewEndpoint()
                        .withAddresses("fd00::20")
                        .withHostname("metrics-0")
                        .withNewConditions(true, true, false)
                    .endEndpoint()
                    .addNewPort(null, "metrics", 9090, "TCP")
                .endItem()
                .addToAdditionalProperties("source", "list-test")
                .build();

        assertThat(copied).isEqualTo(apiSlice);
        assertThat(copied.hashCode()).isEqualTo(apiSlice.hashCode());
        assertThat(copied.toString()).contains("api-slice", "EndpointSlice");
        assertThat(list).isInstanceOf(KubernetesResource.class);
        assertThat(list.getApiVersion()).isEqualTo("discovery.k8s.io/v1");
        assertThat(list.getKind()).isEqualTo("EndpointSliceList");
        assertThat(list.getMetadata().getContinue()).isEqualTo("next-page");
        assertThat(list.getMetadata().getRemainingItemCount()).isEqualTo(1L);
        assertThat(list.getAdditionalProperties()).containsEntry("source", "list-test");
        assertThat(list.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("api-slice", "metrics-slice");
        assertThat(new EndpointSliceListBuilder(list).buildMatchingItem(
                item -> "metrics-slice".equals(item.buildMetadata().getName())))
                .isEqualTo(list.getItems().get(1));

        EndpointSliceList updated = list.toBuilder()
                .editMatchingItem(item -> "api-slice".equals(item.buildMetadata().getName()))
                    .editMetadata()
                        .addToAnnotations("promoted", "true")
                    .endMetadata()
                    .editFirstEndpoint()
                        .withHostname("api-primary")
                    .endEndpoint()
                .endItem()
                .removeMatchingFromItems(item -> "IPv6".equals(item.getAddressType()))
                .build();

        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getItems().get(0).getMetadata().getAnnotations()).containsEntry("promoted", "true");
        assertThat(updated.getItems().get(0).getEndpoints().get(0).getHostname()).isEqualTo("api-primary");
        assertThat(list.getItems()).hasSize(2);
        assertThat(list.getItems().get(0).getEndpoints().get(0).getHostname()).isEqualTo("api-0");
    }

    @Test
    void componentBuildersAndAccessorsHandleConditionsHintsPortsAndAdditionalProperties() {
        EndpointConditions conditions = new EndpointConditionsBuilder()
                .withReady(true)
                .withServing(false)
                .withTerminating(true)
                .addToAdditionalProperties("reason", "draining")
                .build();
        EndpointConditions available = conditions.edit()
                .withServing(true)
                .withTerminating(false)
                .removeFromAdditionalProperties("reason")
                .build();

        EndpointHints hints = new EndpointHintsBuilder()
                .addToForNodes(new ForNodeBuilder().withName("node-a").addToAdditionalProperties("rack", "r1").build())
                .addNewForNode("node-b")
                .addToForZones(new ForZoneBuilder().withName("us-east-1a").build())
                .addNewForZone("us-east-1b")
                .addToAdditionalProperties("hint-source", "scheduler")
                .build();
        EndpointHints trimmedHints = hints.toBuilder()
                .removeMatchingFromForNodes(node -> "node-b".equals(node.getName()))
                .editFirstForZone()
                    .withName("us-east-1c")
                .endForZone()
                .build();

        EndpointPort https = new EndpointPortBuilder()
                .withAppProtocol("HTTPS")
                .withName("https")
                .withPort(443)
                .withProtocol("TCP")
                .addToAdditionalProperties("secure", true)
                .build();
        EndpointPort renamed = https.toBuilder()
                .withName("tls")
                .removeFromAdditionalProperties("secure")
                .build();

        assertThat(conditions.getReady()).isTrue();
        assertThat(conditions.getServing()).isFalse();
        assertThat(conditions.getTerminating()).isTrue();
        assertThat(conditions.getAdditionalProperties()).containsEntry("reason", "draining");
        assertThat(available.getServing()).isTrue();
        assertThat(available.getTerminating()).isFalse();
        assertThat(available.getAdditionalProperties()).doesNotContainKey("reason");
        assertThat(hints.getForNodes()).extracting(ForNode::getName).containsExactly("node-a", "node-b");
        assertThat(hints.getForNodes().get(0).getAdditionalProperties()).containsEntry("rack", "r1");
        assertThat(hints.getForZones()).extracting(ForZone::getName).containsExactly("us-east-1a", "us-east-1b");
        assertThat(hints.getAdditionalProperties()).containsEntry("hint-source", "scheduler");
        assertThat(trimmedHints.getForNodes()).extracting(ForNode::getName).containsExactly("node-a");
        assertThat(trimmedHints.getForZones()).extracting(ForZone::getName).containsExactly("us-east-1c", "us-east-1b");
        assertThat(https.getAppProtocol()).isEqualTo("HTTPS");
        assertThat(https.getName()).isEqualTo("https");
        assertThat(https.getPort()).isEqualTo(443);
        assertThat(https.getProtocol()).isEqualTo("TCP");
        assertThat(renamed.getName()).isEqualTo("tls");
        assertThat(renamed.getAdditionalProperties()).doesNotContainKey("secure");
    }

    @Test
    void endpointSliceBuilderVisitorUpdatesNestedEndpointsAndPorts() {
        EndpointSliceBuilder builder = new EndpointSliceBuilder()
                .withAddressType("IPv4")
                .withNewMetadata()
                    .withName("visitor-slice")
                    .withNamespace("default")
                .endMetadata()
                .addNewEndpoint()
                    .withAddresses("10.3.0.10")
                    .withHostname("blue-0")
                    .withNodeName("node-a")
                    .withZone("us-east-1a")
                .endEndpoint()
                .addNewEndpoint()
                    .withAddresses("10.3.0.11")
                    .withHostname("blue-1")
                    .withNodeName("node-b")
                    .withZone("us-east-1b")
                .endEndpoint()
                .addNewPort(null, "web", 80, "TCP")
                .addNewPort(null, "admin", 9000, "TCP");

        builder.accept(EndpointBuilder.class, endpoint -> {
            if ("blue-0".equals(endpoint.getHostname())) {
                endpoint.withNodeName("node-c")
                        .withZone("us-east-1c")
                        .editOrNewConditions()
                            .withReady(true)
                            .withServing(true)
                            .withTerminating(false)
                        .endConditions();
            }
        });
        builder.accept(EndpointPortBuilder.class, port -> {
            if ("web".equals(port.getName())) {
                port.withAppProtocol("kubernetes.io/ws")
                        .withPort(8080);
            }
        });

        EndpointSlice visited = builder.build();

        assertThat(visited.getEndpoints()).extracting(Endpoint::getHostname)
                .containsExactly("blue-0", "blue-1");
        assertThat(visited.getEndpoints().get(0).getNodeName()).isEqualTo("node-c");
        assertThat(visited.getEndpoints().get(0).getZone()).isEqualTo("us-east-1c");
        assertThat(visited.getEndpoints().get(0).getConditions().getReady()).isTrue();
        assertThat(visited.getEndpoints().get(0).getConditions().getServing()).isTrue();
        assertThat(visited.getEndpoints().get(0).getConditions().getTerminating()).isFalse();
        assertThat(visited.getEndpoints().get(1).getNodeName()).isEqualTo("node-b");
        assertThat(visited.getEndpoints().get(1).getConditions()).isNull();
        assertThat(visited.getPorts()).extracting(EndpointPort::getName)
                .containsExactly("web", "admin");
        assertThat(visited.getPorts().get(0).getAppProtocol()).isEqualTo("kubernetes.io/ws");
        assertThat(visited.getPorts().get(0).getPort()).isEqualTo(8080);
        assertThat(visited.getPorts().get(1).getPort()).isEqualTo(9000);
    }

    @Test
    void betaEndpointSliceModelsTopologyHintsListsAndEdits() {
        io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice betaSlice =
                new io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSliceBuilder()
                .withAddressType("IPv4")
                .withNewMetadata()
                    .withName("legacy-slice")
                    .withNamespace("legacy")
                    .addToLabels("kubernetes.io/service-name", "legacy-service")
                .endMetadata()
                .addNewEndpoint()
                    .withAddresses("10.2.0.10")
                    .withHostname("legacy-0")
                    .withNodeName("legacy-node")
                    .addToTopology("kubernetes.io/hostname", "legacy-node")
                    .addToTopology("topology.kubernetes.io/zone", "us-central1-a")
                    .withNewConditions(true, true, false)
                    .withNewHints()
                        .addNewForZone("us-central1-a")
                    .endHints()
                    .withNewTargetRef()
                        .withApiVersion("v1")
                        .withKind("Pod")
                        .withName("legacy-0")
                        .withNamespace("legacy")
                    .endTargetRef()
                .endEndpoint()
                .addNewPort("http", "web", 8080, "TCP")
                .addToAdditionalProperties("beta", true)
                .build();

        assertThat(betaSlice).isInstanceOf(HasMetadata.class);
        assertThat(betaSlice.getApiVersion()).isEqualTo("discovery.k8s.io/v1beta1");
        assertThat(betaSlice.getKind()).isEqualTo("EndpointSlice");
        assertThat(betaSlice.getMetadata().getName()).isEqualTo("legacy-slice");
        assertThat(betaSlice.getEndpoints().get(0).getTopology())
                .containsEntry("kubernetes.io/hostname", "legacy-node")
                .containsEntry("topology.kubernetes.io/zone", "us-central1-a");
        assertThat(betaSlice.getEndpoints().get(0).getHints().getForZones().get(0).getName())
                .isEqualTo("us-central1-a");
        assertThat(betaSlice.getEndpoints().get(0).getTargetRef().getName()).isEqualTo("legacy-0");
        assertThat(betaSlice.getPorts().get(0).getPort()).isEqualTo(8080);
        assertThat(betaSlice.getAdditionalProperties()).containsEntry("beta", true);

        io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSliceList betaList =
                new io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSliceListBuilder()
                .withMetadata(new ListMetaBuilder()
                        .withContinue("continue-token")
                        .withRemainingItemCount(2L)
                        .withResourceVersion("rv-99")
                        .build())
                .addToItems(betaSlice)
                .addNewItemLike(betaSlice)
                    .editMetadata()
                        .withName("legacy-slice-canary")
                    .endMetadata()
                    .editFirstEndpoint()
                        .withHostname("legacy-canary")
                        .removeFromTopology("kubernetes.io/hostname")
                    .endEndpoint()
                .endItem()
                .build();
        io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSliceList updated = betaList.toBuilder()
                .removeMatchingFromItems(item -> "legacy-slice".equals(item.buildMetadata().getName()))
                .editFirstItem()
                    .editFirstPort()
                        .withPort(8081)
                    .endPort()
                .endItem()
                .build();

        assertThat(betaList.getApiVersion()).isEqualTo("discovery.k8s.io/v1beta1");
        assertThat(betaList.getKind()).isEqualTo("EndpointSliceList");
        assertThat(betaList.getMetadata().getContinue()).isEqualTo("continue-token");
        assertThat(betaList.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("legacy-slice", "legacy-slice-canary");
        assertThat(betaList.getItems().get(1).getEndpoints().get(0).getHostname()).isEqualTo("legacy-canary");
        assertThat(betaList.getItems().get(1).getEndpoints().get(0).getTopology())
                .doesNotContainKey("kubernetes.io/hostname");
        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getItems().get(0).getPorts().get(0).getPort()).isEqualTo(8081);
    }

    @Test
    void serviceLoaderDiscoversDiscoveryResources() {
        List<String> discoveredApiKinds = new ArrayList<>();
        for (KubernetesResource resource : ServiceLoader.load(KubernetesResource.class)) {
            if (resource instanceof HasMetadata metadata) {
                String apiVersion = metadata.getApiVersion();
                if (apiVersion != null && apiVersion.startsWith("discovery.k8s.io/")) {
                    discoveredApiKinds.add(apiVersion + "/" + metadata.getKind());
                }
            }
        }

        assertThat(discoveredApiKinds)
                .contains("discovery.k8s.io/v1/EndpointSlice")
                .contains("discovery.k8s.io/v1beta1/EndpointSlice");
    }
}
