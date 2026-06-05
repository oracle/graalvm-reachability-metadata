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
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPBackendRef;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPHeaderMatch;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPPathMatch;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPPathModifier;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRequestRedirectFilter;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRoute;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteFilter;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteMatch;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteRule;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteSpec;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.Listener;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.ListenerTLSConfig;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.ParentReference;
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
    void httpRouteBuilderCreatesPathMatchRedirectAndBackendRouting() {
        HTTPRoute httpRoute = new HTTPRouteBuilder()
                .withApiVersion("gateway.networking.k8s.io/v1")
                .withKind("HTTPRoute")
                .withNewMetadata()
                .withName("store-route")
                .withNamespace("apps")
                .endMetadata()
                .withNewSpec()
                .withHostnames("shop.example.com")
                .addNewParentRef()
                .withGroup("gateway.networking.k8s.io")
                .withKind("Gateway")
                .withName("edge-gateway")
                .withNamespace("networking")
                .withSectionName("https")
                .endParentRef()
                .addNewRule()
                .withName("redirect-store")
                .addNewMatch()
                .withMethod("GET")
                .withNewPath("PathPrefix", "/store")
                .addNewHeader()
                .withName("X-Redirect")
                .withType("Exact")
                .withValue("enabled")
                .endHeader()
                .endMatch()
                .addNewFilter()
                .withType("RequestRedirect")
                .withNewRequestRedirect()
                .withScheme("https")
                .withHostname("shop.example.com")
                .withPort(443)
                .withStatusCode(308)
                .withNewPath()
                .withType("ReplacePrefixMatch")
                .withReplacePrefixMatch("/catalog")
                .endPath()
                .endRequestRedirect()
                .endFilter()
                .addNewBackendRef()
                .withKind("Service")
                .withName("store-service")
                .withPort(8080)
                .withWeight(100)
                .endBackendRef()
                .endRule()
                .endSpec()
                .build();

        assertThat(httpRoute.getApiVersion()).isEqualTo("gateway.networking.k8s.io/v1");
        assertThat(httpRoute.getKind()).isEqualTo("HTTPRoute");
        assertThat(httpRoute.getMetadata().getName()).isEqualTo("store-route");
        assertThat(httpRoute.getMetadata().getNamespace()).isEqualTo("apps");

        HTTPRouteSpec spec = httpRoute.getSpec();
        assertThat(spec.getHostnames()).containsExactly("shop.example.com");

        List<ParentReference> parentRefs = spec.getParentRefs();
        assertThat(parentRefs).hasSize(1);
        ParentReference parentRef = parentRefs.get(0);
        assertThat(parentRef.getGroup()).isEqualTo("gateway.networking.k8s.io");
        assertThat(parentRef.getKind()).isEqualTo("Gateway");
        assertThat(parentRef.getName()).isEqualTo("edge-gateway");
        assertThat(parentRef.getNamespace()).isEqualTo("networking");
        assertThat(parentRef.getSectionName()).isEqualTo("https");

        List<HTTPRouteRule> rules = spec.getRules();
        assertThat(rules).hasSize(1);
        HTTPRouteRule rule = rules.get(0);
        assertThat(rule.getName()).isEqualTo("redirect-store");

        List<HTTPRouteMatch> matches = rule.getMatches();
        assertThat(matches).hasSize(1);
        HTTPRouteMatch match = matches.get(0);
        assertThat(match.getMethod()).isEqualTo("GET");
        HTTPPathMatch path = match.getPath();
        assertThat(path.getType()).isEqualTo("PathPrefix");
        assertThat(path.getValue()).isEqualTo("/store");

        List<HTTPHeaderMatch> headers = match.getHeaders();
        assertThat(headers).hasSize(1);
        HTTPHeaderMatch header = headers.get(0);
        assertThat(header.getName()).isEqualTo("X-Redirect");
        assertThat(header.getType()).isEqualTo("Exact");
        assertThat(header.getValue()).isEqualTo("enabled");

        List<HTTPRouteFilter> filters = rule.getFilters();
        assertThat(filters).hasSize(1);
        HTTPRouteFilter filter = filters.get(0);
        assertThat(filter.getType()).isEqualTo("RequestRedirect");
        HTTPRequestRedirectFilter redirect = filter.getRequestRedirect();
        assertThat(redirect.getScheme()).isEqualTo("https");
        assertThat(redirect.getHostname()).isEqualTo("shop.example.com");
        assertThat(redirect.getPort()).isEqualTo(443);
        assertThat(redirect.getStatusCode()).isEqualTo(308);
        HTTPPathModifier redirectPath = redirect.getPath();
        assertThat(redirectPath.getType()).isEqualTo("ReplacePrefixMatch");
        assertThat(redirectPath.getReplacePrefixMatch()).isEqualTo("/catalog");

        List<HTTPBackendRef> backendRefs = rule.getBackendRefs();
        assertThat(backendRefs).hasSize(1);
        HTTPBackendRef backendRef = backendRefs.get(0);
        assertThat(backendRef.getKind()).isEqualTo("Service");
        assertThat(backendRef.getName()).isEqualTo("store-service");
        assertThat(backendRef.getPort()).isEqualTo(8080);
        assertThat(backendRef.getWeight()).isEqualTo(100);
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
