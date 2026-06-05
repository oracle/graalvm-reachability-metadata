/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_gatewayapi;

import java.util.List;

import io.fabric8.kubernetes.api.model.gatewayapi.v1.AllowedRoutes;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.Gateway;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.GatewayBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.GatewaySpec;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.Listener;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.ListenerTLSConfig;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.RouteGroupKind;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.RouteNamespaces;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.SecretObjectReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Kubernetes_model_gatewayapiTest {
    @Test
    void gatewayBuilderCreatesHttpsListenerWithTlsAndRoutePolicy() {
        Gateway gateway = new GatewayBuilder()
                .withApiVersion("gateway.networking.k8s.io/v1")
                .withKind("Gateway")
                .withNewMetadata()
                .withName("edge-gateway")
                .withNamespace("networking")
                .endMetadata()
                .withNewSpec()
                .withGatewayClassName("public-gateway")
                .addNewListener()
                .withName("https")
                .withHostname("example.com")
                .withPort(443)
                .withProtocol("HTTPS")
                .withNewTls()
                .withMode("Terminate")
                .addNewCertificateRef("", "Secret", "example-com-tls", "networking")
                .endTls()
                .withNewAllowedRoutes()
                .withNewNamespaces()
                .withFrom("Same")
                .endNamespaces()
                .addNewKind()
                .withGroup("gateway.networking.k8s.io")
                .withKind("HTTPRoute")
                .endKind()
                .endAllowedRoutes()
                .endListener()
                .endSpec()
                .build();

        assertThat(gateway.getApiVersion()).isEqualTo("gateway.networking.k8s.io/v1");
        assertThat(gateway.getKind()).isEqualTo("Gateway");
        assertThat(gateway.getMetadata().getName()).isEqualTo("edge-gateway");
        assertThat(gateway.getMetadata().getNamespace()).isEqualTo("networking");

        GatewaySpec spec = gateway.getSpec();
        assertThat(spec.getGatewayClassName()).isEqualTo("public-gateway");

        List<Listener> listeners = spec.getListeners();
        assertThat(listeners).hasSize(1);

        Listener listener = listeners.get(0);
        assertThat(listener.getName()).isEqualTo("https");
        assertThat(listener.getHostname()).isEqualTo("example.com");
        assertThat(listener.getPort()).isEqualTo(443);
        assertThat(listener.getProtocol()).isEqualTo("HTTPS");

        ListenerTLSConfig tls = listener.getTls();
        assertThat(tls.getMode()).isEqualTo("Terminate");
        assertThat(tls.getCertificateRefs()).hasSize(1);

        SecretObjectReference certificateRef = tls.getCertificateRefs().get(0);
        assertThat(certificateRef.getGroup()).isEmpty();
        assertThat(certificateRef.getKind()).isEqualTo("Secret");
        assertThat(certificateRef.getName()).isEqualTo("example-com-tls");
        assertThat(certificateRef.getNamespace()).isEqualTo("networking");

        AllowedRoutes allowedRoutes = listener.getAllowedRoutes();
        RouteNamespaces namespaces = allowedRoutes.getNamespaces();
        assertThat(namespaces.getFrom()).isEqualTo("Same");

        List<RouteGroupKind> routeKinds = allowedRoutes.getKinds();
        assertThat(routeKinds).hasSize(1);
        assertThat(routeKinds.get(0).getGroup()).isEqualTo("gateway.networking.k8s.io");
        assertThat(routeKinds.get(0).getKind()).isEqualTo("HTTPRoute");
    }

    @Test
    void gatewayBuilderEditsMatchingListenerWithoutDroppingExistingTls() {
        Gateway gateway = new GatewayBuilder()
                .withNewMetadata()
                .withName("edge-gateway")
                .endMetadata()
                .withNewSpec()
                .withGatewayClassName("public-gateway")
                .addNewListener()
                .withName("https")
                .withHostname("example.com")
                .withPort(443)
                .withProtocol("HTTPS")
                .withNewTls()
                .withMode("Terminate")
                .addNewCertificateRef("", "Secret", "example-com-tls", "networking")
                .endTls()
                .endListener()
                .endSpec()
                .build();

        Gateway updatedGateway = new GatewayBuilder(gateway)
                .editSpec()
                .editMatchingListener(listener -> "https".equals(listener.getName()))
                .withHostname("api.example.com")
                .endListener()
                .endSpec()
                .build();

        Listener updatedListener = updatedGateway.getSpec().getListeners().get(0);
        assertThat(updatedListener.getHostname()).isEqualTo("api.example.com");
        assertThat(updatedListener.getName()).isEqualTo("https");
        assertThat(updatedListener.getPort()).isEqualTo(443);
        ListenerTLSConfig updatedTls = updatedListener.getTls();
        assertThat(updatedTls.getMode()).isEqualTo("Terminate");
        assertThat(updatedTls.getCertificateRefs().get(0).getName()).isEqualTo("example-com-tls");
    }
}
