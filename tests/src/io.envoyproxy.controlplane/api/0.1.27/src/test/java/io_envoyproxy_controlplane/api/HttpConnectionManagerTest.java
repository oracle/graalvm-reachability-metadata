/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_envoyproxy_controlplane.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.extensions.filters.http.router.v3.Router;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager.CodecType;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager.ForwardClientCertDetails;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager.ServerHeaderTransformation;
import org.junit.jupiter.api.Test;

public class HttpConnectionManagerTest {
    @Test
    void roundTripsListenerSettingsIncludingNestedPolicies() throws Exception {
        JsonFormat.TypeRegistry registry = JsonFormat.TypeRegistry.newBuilder().add(Router.getDescriptor()).build();
        HttpConnectionManager.Builder builder = HttpConnectionManager.newBuilder();
        JsonFormat.parser().usingTypeRegistry(registry).merge("""
                {
                  "statPrefix": "ingress",
                  "codecType": "HTTP2",
                  "routeConfig": {"name": "application-routes"},
                  "httpFilters": [{
                    "name": "envoy.filters.http.router",
                    "typedConfig": {
                      "@type": "type.googleapis.com/envoy.extensions.filters.http.router.v3.Router",
                      "startChildSpan": true
                    }
                  }],
                  "commonHttpProtocolOptions": {"idleTimeout": "60s"},
                  "httpProtocolOptions": {"enableTrailers": true},
                  "http2ProtocolOptions": {"maxConcurrentStreams": 100},
                  "serverName": "edge-proxy",
                  "serverHeaderTransformation": "APPEND_IF_ABSENT",
                  "forwardClientCertDetails": "SANITIZE_SET",
                  "setCurrentClientCertDetails": {"subject": true, "cert": true, "chain": true, "dns": true, "uri": true},
                  "internalAddressConfig": {"unixSockets": true},
                  "tracing": {"randomSampling": {"value": 25}, "verbose": true, "maxPathTagLength": 128},
                  "upgradeConfigs": [{"upgradeType": "websocket", "enabled": true}],
                  "localReplyConfig": {"bodyFormat": {"textFormat": "%LOCAL_REPLY_BODY%"}}
                }
                """, builder);
        HttpConnectionManager manager = builder.build();
        assertEquals("ingress", manager.getStatPrefix());
        assertEquals(CodecType.HTTP2, manager.getCodecType());
        assertEquals("application-routes", manager.getRouteConfig().getName());
        assertTrue(manager.getHttpFilters(0).getTypedConfig().unpack(Router.class).getStartChildSpan());
        assertEquals(60, manager.getCommonHttpProtocolOptions().getIdleTimeout().getSeconds());
        assertTrue(manager.getHttpProtocolOptions().getEnableTrailers());
        assertEquals(100, manager.getHttp2ProtocolOptions().getMaxConcurrentStreams().getValue());
        assertEquals("edge-proxy", manager.getServerName());
        assertEquals(ServerHeaderTransformation.APPEND_IF_ABSENT, manager.getServerHeaderTransformation());
        assertEquals(ForwardClientCertDetails.SANITIZE_SET, manager.getForwardClientCertDetails());
        assertTrue(manager.getSetCurrentClientCertDetails().getSubject().getValue());
        assertTrue(manager.getSetCurrentClientCertDetails().getCert());
        assertTrue(manager.getSetCurrentClientCertDetails().getChain());
        assertTrue(manager.getSetCurrentClientCertDetails().getDns());
        assertTrue(manager.getSetCurrentClientCertDetails().getUri());
        assertTrue(manager.getInternalAddressConfig().getUnixSockets());
        assertEquals(25.0, manager.getTracing().getRandomSampling().getValue());
        assertTrue(manager.getTracing().getVerbose());
        assertEquals(128, manager.getTracing().getMaxPathTagLength().getValue());
        assertEquals("websocket", manager.getUpgradeConfigs(0).getUpgradeType());
        assertTrue(manager.getUpgradeConfigs(0).getEnabled().getValue());
        assertEquals("%LOCAL_REPLY_BODY%", manager.getLocalReplyConfig().getBodyFormat().getTextFormat());

        HttpConnectionManager.Builder copy = HttpConnectionManager.newBuilder();
        JsonFormat.parser().usingTypeRegistry(registry).merge(
                JsonFormat.printer().usingTypeRegistry(registry).print(manager), copy);
        assertEquals(manager, copy.build());
        assertEquals(manager, HttpConnectionManager.parseFrom(manager.toByteArray()));
    }

    @Test
    void replacesInlineRoutesWithDynamicRouteSubscription() throws Exception {
        HttpConnectionManager.Builder builder = HttpConnectionManager.newBuilder();
        JsonFormat.parser().merge("""
                {"statPrefix": "dynamic", "routeConfig": {"name": "inline-routes"}}
                """, builder);
        builder.getRdsBuilder().setRouteConfigName("dynamic-routes").getConfigSourceBuilder().getAdsBuilder();
        assertFalse(builder.hasRouteConfig());
        assertTrue(builder.hasRds());

        HttpConnectionManager.Builder copy = HttpConnectionManager.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().print(builder), copy);
        assertEquals("dynamic-routes", copy.getRds().getRouteConfigName());
        assertTrue(copy.getRds().getConfigSource().hasAds());
        assertEquals(builder.build(), copy.build());
    }
}
