/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor_netty_incubator.reactor_netty_incubator_quic;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.netty.channel.ChannelOption;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicCongestionControlAlgorithm;
import io.netty.incubator.codec.quic.QuicConnectionIdGenerator;
import io.netty.incubator.codec.quic.QuicSslEngine;
import io.netty.util.AttributeKey;
import org.junit.jupiter.api.Test;
import reactor.netty.incubator.quic.QuicClient;
import reactor.netty.incubator.quic.QuicClientConfig;
import reactor.netty.incubator.quic.QuicInitialSettingsSpec;
import reactor.netty.incubator.quic.QuicResources;
import reactor.netty.incubator.quic.QuicServer;
import reactor.netty.incubator.quic.QuicServerConfig;
import reactor.netty.resources.LoopResources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Reactor_netty_incubator_quicTest {
    @Test
    void initialSettingsExposeValueSemanticsAndValidation() {
        QuicInitialSettingsSpec settings = QuicClient.create()
                .initialSettings(builder -> builder
                        .maxData(1_000)
                        .maxStreamDataBidirectionalLocal(2_000)
                        .maxStreamDataBidirectionalRemote(3_000)
                        .maxStreamDataUnidirectional(4_000)
                        .maxStreamsBidirectional(5)
                        .maxStreamsUnidirectional(6))
                .configuration()
                .initialSettings();
        QuicInitialSettingsSpec sameSettings = QuicServer.create()
                .initialSettings(builder -> builder
                        .maxData(1_000)
                        .maxStreamDataBidirectionalLocal(2_000)
                        .maxStreamDataBidirectionalRemote(3_000)
                        .maxStreamDataUnidirectional(4_000)
                        .maxStreamsBidirectional(5)
                        .maxStreamsUnidirectional(6))
                .configuration()
                .initialSettings();
        QuicInitialSettingsSpec differentSettings = QuicClient.create()
                .initialSettings(builder -> builder.maxData(1_001))
                .configuration()
                .initialSettings();

        assertThat(settings.maxData()).isEqualTo(1_000);
        assertThat(settings.maxStreamDataBidirectionalLocal()).isEqualTo(2_000);
        assertThat(settings.maxStreamDataBidirectionalRemote()).isEqualTo(3_000);
        assertThat(settings.maxStreamDataUnidirectional()).isEqualTo(4_000);
        assertThat(settings.maxStreamsBidirectional()).isEqualTo(5);
        assertThat(settings.maxStreamsUnidirectional()).isEqualTo(6);
        assertThat(settings).isEqualTo(sameSettings).hasSameHashCodeAs(sameSettings);
        assertThat(settings).isNotEqualTo(differentSettings);

        assertThatThrownBy(() -> QuicClient.create().initialSettings(builder -> builder.maxData(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxData");
        assertThatThrownBy(() -> QuicClient.create().initialSettings(
                builder -> builder.maxStreamDataBidirectionalLocal(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxStreamDataBidirectionalLocal");
        assertThatThrownBy(() -> QuicClient.create().initialSettings(
                builder -> builder.maxStreamDataBidirectionalRemote(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxStreamDataBidirectionalRemote");
        assertThatThrownBy(() -> QuicClient.create().initialSettings(
                builder -> builder.maxStreamDataUnidirectional(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxStreamDataUnidirectional");
        assertThatThrownBy(() -> QuicClient.create().initialSettings(builder -> builder.maxStreamsBidirectional(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxStreamsBidirectional");
        assertThatThrownBy(() -> QuicClient.create().initialSettings(builder -> builder.maxStreamsUnidirectional(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxStreamsUnidirectional");
    }

    @Test
    void clientConfigurationCapturesImmutableTransportOptions() {
        AttributeKey<String> streamAttribute = AttributeKey.valueOf("reactor.netty.quic.test.client.streamAttribute");
        InetSocketAddress bindAddress = new InetSocketAddress("127.0.0.1", 0);
        InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 12_345);

        QuicClient client = QuicClient.create()
                .bindAddress(() -> bindAddress)
                .remoteAddress(() -> remoteAddress)
                .ackDelayExponent(7)
                .activeMigration(false)
                .congestionControlAlgorithm(QuicCongestionControlAlgorithm.RENO)
                .datagram(8, 9)
                .grease(false)
                .hystart(false)
                .idleTimeout(Duration.ofMillis(123))
                .initialSettings(builder -> builder.maxData(10).maxStreamsBidirectional(11))
                .localConnectionIdLength(12)
                .maxAckDelay(Duration.ofMillis(45))
                .maxRecvUdpPayloadSize(4_096)
                .maxSendUdpPayloadSize(1_400)
                .streamAttr(streamAttribute, "value")
                .streamOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1_234);
        QuicClientConfig config = client.configuration();

        assertThat(config.bindAddress().get()).isSameAs(bindAddress);
        assertThat(config.remoteAddress().get()).isSameAs(remoteAddress);
        assertThat(config.ackDelayExponent()).isEqualTo(7);
        assertThat(config.isActiveMigration()).isFalse();
        assertThat(config.congestionControlAlgorithm()).isSameAs(QuicCongestionControlAlgorithm.RENO);
        assertThat(config.recvQueueLen()).isEqualTo(8);
        assertThat(config.sendQueueLen()).isEqualTo(9);
        assertThat(config.isGrease()).isFalse();
        assertThat(config.isHystart()).isFalse();
        assertThat(config.idleTimeout()).isEqualTo(Duration.ofMillis(123));
        assertThat(config.initialSettings().maxData()).isEqualTo(10);
        assertThat(config.initialSettings().maxStreamsBidirectional()).isEqualTo(11);
        assertThat(config.localConnectionIdLength()).isEqualTo(12);
        assertThat(config.maxAckDelay()).isEqualTo(Duration.ofMillis(45));
        assertThat(config.maxRecvUdpPayloadSize()).isEqualTo(4_096);
        assertThat(config.maxSendUdpPayloadSize()).isEqualTo(1_400);
        assertThat(config.streamAttributes().get(streamAttribute)).isEqualTo("value");
        assertThat(config.streamOptions().get(ChannelOption.AUTO_READ)).isEqualTo(false);
        assertThat(config.streamOptions().get(ChannelOption.CONNECT_TIMEOUT_MILLIS)).isEqualTo(1_234);

        assertThatThrownBy(() -> config.streamAttributes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> config.streamOptions().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        QuicClient original = QuicClient.create();
        assertThat(original.ackDelayExponent(3)).isSameAs(original);
        assertThat(original.activeMigration(true)).isSameAs(original);
        assertThat(original.grease(true)).isSameAs(original);
        assertThat(original.hystart(true)).isSameAs(original);
        assertThat(original.localConnectionIdLength(20)).isSameAs(original);
        assertThat(original.maxAckDelay(Duration.ofMillis(25))).isSameAs(original);
        assertThat(original.maxSendUdpPayloadSize(1_200)).isSameAs(original);
    }

    @Test
    void serverConfigurationCapturesServerSpecificAndStreamOptions() {
        AttributeKey<String> streamAttribute = AttributeKey.valueOf("reactor.netty.quic.test.server.streamAttribute");
        InetSocketAddress bindAddress = new InetSocketAddress("127.0.0.1", 0);

        QuicServer server = QuicServer.create()
                .bindAddress(() -> bindAddress)
                .host("localhost")
                .port(0)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .connectionIdAddressGenerator(QuicConnectionIdGenerator.signGenerator())
                .ackDelayExponent(4)
                .datagram(2, 3)
                .streamAttr(streamAttribute, "stream")
                .streamOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000);
        QuicServerConfig config = server.configuration();

        assertThat(config.bindAddress().get()).isInstanceOf(InetSocketAddress.class);
        assertThat(((InetSocketAddress) config.bindAddress().get()).getPort()).isZero();
        assertThat(config.tokenHandler()).isSameAs(InsecureQuicTokenHandler.INSTANCE);
        assertThat(config.connectionIdAddressGenerator().isIdempotent()).isTrue();
        assertThat(config.ackDelayExponent()).isEqualTo(4);
        assertThat(config.recvQueueLen()).isEqualTo(2);
        assertThat(config.sendQueueLen()).isEqualTo(3);
        assertThat(config.streamAttributes().get(streamAttribute)).isEqualTo("stream");
        assertThat(config.streamOptions().get(ChannelOption.AUTO_READ)).isEqualTo(false);
        assertThat(config.streamOptions().get(ChannelOption.CONNECT_TIMEOUT_MILLIS)).isEqualTo(2_000);

        QuicServer withoutAttribute = server.streamAttr(streamAttribute, null);
        QuicServer withoutOption = server.streamOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, null);
        assertThat(withoutAttribute.configuration().streamAttributes()).doesNotContainKey(streamAttribute);
        assertThat(withoutOption.configuration().streamOptions())
                .doesNotContainKey(ChannelOption.CONNECT_TIMEOUT_MILLIS);
    }

    @Test
    void invalidTransportArgumentsFailFast() {
        QuicClient client = QuicClient.create();

        assertThatThrownBy(() -> client.ackDelayExponent(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.datagram(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.datagram(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.localConnectionIdLength(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.localConnectionIdLength(21)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.maxRecvUdpPayloadSize(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.maxSendUdpPayloadSize(-1)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.host(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.remoteAddress(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.bindAddress(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.idleTimeout(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.maxAckDelay(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.initialSettings(null)).isInstanceOf(NullPointerException.class);
        Function<QuicChannel, QuicSslEngine> nullSslEngineProvider = null;
        assertThatThrownBy(() -> client.congestionControlAlgorithm(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.secure(nullSslEngineProvider)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.streamAttr(null, "value")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.streamOption(null, Boolean.FALSE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.streamObserve(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.handleStream(null)).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> QuicServer.create().tokenHandler(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> QuicServer.create().connectionIdAddressGenerator(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> QuicServer.create().doOnConnection(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void callbackConfigurationComposesCallbacksInRegistrationOrder() {
        AtomicInteger clientOrder = new AtomicInteger();
        QuicClient client = QuicClient.create()
                .doOnConnect(config -> assertThat(clientOrder.compareAndSet(0, 1)).isTrue())
                .doOnConnect(config -> assertThat(clientOrder.compareAndSet(1, 2)).isTrue())
                .doOnConnected(connection -> assertThat(clientOrder.compareAndSet(2, 3)).isTrue())
                .doOnDisconnected(connection -> assertThat(clientOrder.compareAndSet(3, 4)).isTrue());
        QuicClientConfig clientConfig = client.configuration();

        clientConfig.doOnConnect().accept(clientConfig);
        clientConfig.doOnConnected().accept(null);
        clientConfig.doOnDisconnected().accept(null);
        assertThat(clientOrder).hasValue(4);

        AtomicInteger serverOrder = new AtomicInteger();
        QuicServer server = QuicServer.create()
                .doOnBind(config -> assertThat(serverOrder.compareAndSet(0, 1)).isTrue())
                .doOnBind(config -> assertThat(serverOrder.compareAndSet(1, 2)).isTrue())
                .doOnBound(connection -> assertThat(serverOrder.compareAndSet(2, 3)).isTrue())
                .doOnUnbound(connection -> assertThat(serverOrder.compareAndSet(3, 4)).isTrue())
                .doOnConnection(connection -> assertThat(serverOrder.compareAndSet(4, 5)).isTrue());
        QuicServerConfig serverConfig = server.configuration();

        serverConfig.doOnBind().accept(serverConfig);
        serverConfig.doOnBound().accept(null);
        serverConfig.doOnUnbound().accept(null);
        serverConfig.doOnConnection().accept(null);
        assertThat(serverOrder).hasValue(5);
    }

    @Test
    void connectAndBindValidateRequiredQuicMaterialBeforeStartingTransport() {
        assertThatThrownBy(() -> QuicClient.create().connect())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sslEngineProvider");
        assertThatThrownBy(() -> QuicServer.create().bind())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sslEngineProvider");
        assertThatThrownBy(() -> QuicServer.create().secure(channel -> null).bind())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tokenHandler");
    }

    @Test
    void defaultQuicResourcesCanBeReplacedAndDisposed() {
        QuicResources.disposeLoopsLater(Duration.ZERO, Duration.ofMillis(100)).block(Duration.ofSeconds(2));
        LoopResources loops = LoopResources.create("quic-test", 1, true);

        try {
            QuicResources resources = QuicResources.set(loops);

            assertThat(QuicResources.get()).isSameAs(resources);
            assertThat(resources.daemon()).isEqualTo(loops.daemon());
            resources.dispose();
            resources.disposeLater().block(Duration.ofSeconds(2));
            assertThat(QuicResources.get()).isSameAs(resources);

            QuicResources.disposeLoopsLater(Duration.ZERO, Duration.ofMillis(100)).block(Duration.ofSeconds(2));
            assertThat(QuicResources.get()).isNotSameAs(resources);
        } finally {
            QuicResources.disposeLoopsLater(Duration.ZERO, Duration.ofMillis(100)).block(Duration.ofSeconds(2));
            QuicResources.reset();
            QuicResources.disposeLoopsLater(Duration.ZERO, Duration.ofMillis(100)).block(Duration.ofSeconds(2));
        }
    }

    @Test
    void defaultConfigurationUsesDocumentedQuicDefaults() {
        QuicClientConfig clientConfig = QuicClient.create().configuration();
        QuicServerConfig serverConfig = QuicServer.create().configuration();

        assertDefaultTransportConfiguration(clientConfig.ackDelayExponent(), clientConfig.isActiveMigration(),
                clientConfig.congestionControlAlgorithm(), clientConfig.isGrease(), clientConfig.isHystart(),
                clientConfig.initialSettings(), clientConfig.localConnectionIdLength(), clientConfig.maxAckDelay(),
                clientConfig.maxRecvUdpPayloadSize(), clientConfig.maxSendUdpPayloadSize(),
                clientConfig.streamOptions());
        assertDefaultTransportConfiguration(serverConfig.ackDelayExponent(), serverConfig.isActiveMigration(),
                serverConfig.congestionControlAlgorithm(), serverConfig.isGrease(), serverConfig.isHystart(),
                serverConfig.initialSettings(), serverConfig.localConnectionIdLength(), serverConfig.maxAckDelay(),
                serverConfig.maxRecvUdpPayloadSize(), serverConfig.maxSendUdpPayloadSize(),
                serverConfig.streamOptions());

        assertThat(clientConfig.remoteAddress().get()).isInstanceOf(InetSocketAddress.class);
        assertThat(((InetSocketAddress) clientConfig.remoteAddress().get()).getPort()).isEqualTo(12_012);
        assertThat(serverConfig.tokenHandler()).isNull();
        assertThat(serverConfig.connectionIdAddressGenerator()).isNotNull();
    }

    private static void assertDefaultTransportConfiguration(
            long ackDelayExponent,
            boolean activeMigration,
            QuicCongestionControlAlgorithm congestionControlAlgorithm,
            boolean grease,
            boolean hystart,
            QuicInitialSettingsSpec initialSettings,
            int localConnectionIdLength,
            Duration maxAckDelay,
            long maxRecvUdpPayloadSize,
            long maxSendUdpPayloadSize,
            Map<ChannelOption<?>, ?> streamOptions
    ) {
        assertThat(ackDelayExponent).isEqualTo(3);
        assertThat(activeMigration).isTrue();
        assertThat(congestionControlAlgorithm).isSameAs(QuicCongestionControlAlgorithm.CUBIC);
        assertThat(grease).isTrue();
        assertThat(hystart).isTrue();
        assertThat(initialSettings.maxData()).isZero();
        assertThat(initialSettings.maxStreamDataBidirectionalLocal()).isZero();
        assertThat(initialSettings.maxStreamDataBidirectionalRemote()).isZero();
        assertThat(initialSettings.maxStreamDataUnidirectional()).isZero();
        assertThat(initialSettings.maxStreamsBidirectional()).isZero();
        assertThat(initialSettings.maxStreamsUnidirectional()).isZero();
        assertThat(localConnectionIdLength).isEqualTo(20);
        assertThat(maxAckDelay).isEqualTo(Duration.ofMillis(25));
        assertThat(maxRecvUdpPayloadSize).isEqualTo(65_527);
        assertThat(maxSendUdpPayloadSize).isEqualTo(1_200);
        assertThat(streamOptions.get(ChannelOption.AUTO_READ)).isEqualTo(false);
    }
}
