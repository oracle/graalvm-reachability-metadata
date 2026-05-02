/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor_netty.reactor_netty_core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.util.AttributeKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.ByteBufMono;
import reactor.netty.Connection;
import reactor.netty.ConnectionObserver;
import reactor.netty.DisposableServer;
import reactor.netty.ReactorNetty;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;
import reactor.netty.transport.NameResolverProvider;
import reactor.netty.transport.ProxyProvider;
import reactor.netty.udp.UdpClient;
import reactor.netty.udp.UdpServer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class Reactor_netty_coreTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String LOCALHOST = "127.0.0.1";

    @TempDir
    Path tempDir;

    @Test
    void byteBufMonoAndFluxConvertPayloadsAcrossRepresentations() {
        ByteBufMono single = ByteBufMono.fromString(
                Mono.just("héllo"),
                StandardCharsets.UTF_8,
                UnpooledByteBufAllocatorHolder.ALLOCATOR);

        assertThat(single.asString(StandardCharsets.UTF_8).block(TIMEOUT)).isEqualTo("héllo");
        assertThat(ByteBufMono.fromString(Mono.just("abc")).asByteArray().block(TIMEOUT))
                .containsExactly((byte) 'a', (byte) 'b', (byte) 'c');

        ByteBuffer byteBuffer = ByteBufFlux.fromString(Flux.just("one", "-", "two"))
                .aggregate()
                .asByteBuffer()
                .block(TIMEOUT);

        assertThat(StandardCharsets.UTF_8.decode(byteBuffer).toString()).isEqualTo("one-two");
    }

    @Test
    void byteBufFluxReadsPathInBoundedChunks() throws Exception {
        Path payload = tempDir.resolve("payload.txt");
        Files.write(payload, "alpha-beta-gamma".getBytes(StandardCharsets.UTF_8));

        List<String> chunks = ByteBufFlux.fromPath(payload, 5)
                .asString(StandardCharsets.UTF_8)
                .collectList()
                .block(TIMEOUT);

        assertThat(chunks).containsExactly("alpha", "-beta", "-gamm", "a");
        assertThat(String.join("", chunks)).isEqualTo("alpha-beta-gamma");
    }

    @Test
    void tcpClientAndServerExchangeDataAndPublishLifecycleCallbacks() throws Exception {
        LoopResources loops = LoopResources.create("rn-tcp-test", 1, 1, true);
        CountDownLatch serverBound = new CountDownLatch(1);
        CountDownLatch serverConnection = new CountDownLatch(1);
        CountDownLatch serverUnbound = new CountDownLatch(1);
        CountDownLatch clientConnected = new CountDownLatch(1);
        CountDownLatch clientDisconnected = new CountDownLatch(1);
        AtomicBoolean serverChannelActive = new AtomicBoolean();
        AttributeKey<String> clientAttribute = AttributeKey.valueOf("reactor-netty-test-attribute");
        DisposableServer server = null;
        Connection client = null;

        try {
            server = TcpServer.create()
                    .host(LOCALHOST)
                    .port(0)
                    .runOn(loops)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .doOnBound(disposableServer -> serverBound.countDown())
                    .doOnConnection(connection -> {
                        serverChannelActive.set(connection.channel().isActive());
                        serverConnection.countDown();
                    })
                    .doOnUnbound(disposableServer -> serverUnbound.countDown())
                    .handle((inbound, outbound) -> inbound.receive()
                            .asString(StandardCharsets.UTF_8)
                            .take(1)
                            .flatMap(message -> outbound.sendString(
                                    Mono.just("echo:" + message.toUpperCase(Locale.ROOT)),
                                    StandardCharsets.UTF_8).then()))
                    .bindNow(TIMEOUT);

            client = TcpClient.newConnection()
                    .host(LOCALHOST)
                    .port(server.port())
                    .runOn(loops)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000)
                    .attr(clientAttribute, "client-value")
                    .doOnConnected(connection -> clientConnected.countDown())
                    .doOnDisconnected(connection -> clientDisconnected.countDown())
                    .connectNow(TIMEOUT);

            client.outbound()
                    .sendString(Mono.just("hello"), StandardCharsets.UTF_8)
                    .then()
                    .block(TIMEOUT);
            String response = client.inbound()
                    .receive()
                    .asString(StandardCharsets.UTF_8)
                    .next()
                    .block(TIMEOUT);

            assertThat(response).isEqualTo("echo:HELLO");
            assertThat(client.channel().attr(clientAttribute).get()).isEqualTo("client-value");
            assertThat(serverBound.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(serverConnection.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(clientConnected.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(serverChannelActive.get()).isTrue();
        } finally {
            if (client != null) {
                client.disposeNow(TIMEOUT);
            }
            if (server != null) {
                server.disposeNow(TIMEOUT);
            }
            loops.disposeLater(Duration.ZERO, TIMEOUT).block(TIMEOUT);
        }

        assertThat(clientDisconnected.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(serverUnbound.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void udpClientSendsDatagramToServer() throws Exception {
        LoopResources loops = LoopResources.create("rn-udp-test", 1, true);
        CountDownLatch serverBound = new CountDownLatch(1);
        CountDownLatch messageReceived = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        Connection server = null;
        Connection client = null;

        try {
            server = UdpServer.create()
                    .host(LOCALHOST)
                    .port(0)
                    .runOn(loops)
                    .doOnBound(connection -> serverBound.countDown())
                    .handle((inbound, outbound) -> inbound.receive()
                            .asString(StandardCharsets.UTF_8)
                            .next()
                            .doOnNext(message -> {
                                receivedMessage.set(message);
                                messageReceived.countDown();
                            })
                            .then())
                    .bindNow(TIMEOUT);

            int serverPort = ((InetSocketAddress) server.address()).getPort();
            client = UdpClient.create()
                    .host(LOCALHOST)
                    .port(serverPort)
                    .runOn(loops)
                    .handle((inbound, outbound) -> outbound.sendString(
                            Mono.just("udp-payload"),
                            StandardCharsets.UTF_8).then())
                    .connectNow(TIMEOUT);

            assertThat(serverBound.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(messageReceived.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(receivedMessage.get()).isEqualTo("udp-payload");
        } finally {
            if (client != null) {
                client.disposeNow(TIMEOUT);
            }
            if (server != null) {
                server.disposeNow(TIMEOUT);
            }
            loops.disposeLater(Duration.ZERO, TIMEOUT).block(TIMEOUT);
        }
    }

    @Test
    void tcpClientObserverSeesConfiguredAndConnectedStates() {
        LoopResources loops = LoopResources.create("rn-observer-test", 1, true);
        AtomicReference<ConnectionObserver.State> lastObservedState = new AtomicReference<>();
        DisposableServer server = null;
        Connection client = null;

        try {
            server = TcpServer.create()
                    .host(LOCALHOST)
                    .port(0)
                    .runOn(loops)
                    .handle((inbound, outbound) -> outbound.neverComplete())
                    .bindNow(TIMEOUT);

            client = TcpClient.newConnection()
                    .host(LOCALHOST)
                    .port(server.port())
                    .runOn(loops)
                    .observe((connection, newState) -> lastObservedState.set(newState))
                    .connectNow(TIMEOUT);

            assertThat(client.channel().isActive()).isTrue();
            assertThat(lastObservedState.get()).isIn(
                    ConnectionObserver.State.CONFIGURED,
                    ConnectionObserver.State.CONNECTED);
        } finally {
            if (client != null) {
                client.disposeNow(TIMEOUT);
            }
            if (server != null) {
                server.disposeNow(TIMEOUT);
            }
            loops.disposeLater(Duration.ZERO, TIMEOUT).block(TIMEOUT);
        }
    }

    @Test
    void connectionProviderBuilderExposesPoolConfigurationAndHostOverrides() {
        SocketAddress remoteAddress = InetSocketAddress.createUnresolved("example.com", 443);
        ConnectionProvider provider = ConnectionProvider.builder("primary-pool")
                .maxConnections(3)
                .pendingAcquireMaxCount(1)
                .pendingAcquireTimeout(Duration.ofMillis(250))
                .maxIdleTime(Duration.ofSeconds(2))
                .maxLifeTime(Duration.ofSeconds(3))
                .forRemoteHost(remoteAddress, spec -> spec.maxConnections(2))
                .build();
        ConnectionProvider mutated = null;

        try {
            assertThat(provider.name()).isEqualTo("primary-pool");
            assertThat(provider.maxConnections()).isEqualTo(3);
            assertThat(provider.maxConnectionsPerHost()).containsEntry(remoteAddress, 2);

            mutated = provider.mutate()
                    .name("mutated-pool")
                    .maxConnections(4)
                    .build();

            assertThat(mutated.name()).isEqualTo("mutated-pool");
            assertThat(mutated.maxConnections()).isEqualTo(4);
            assertThat(mutated.maxConnectionsPerHost()).containsEntry(remoteAddress, 2);
        } finally {
            provider.disposeLater().block(TIMEOUT);
            if (mutated != null) {
                mutated.disposeLater().block(TIMEOUT);
            }
        }
    }

    @Test
    void loopResourcesCreateEventLoopGroupsAndResolveChannelClass() {
        LoopResources loops = LoopResources.create("rn-loop-test", 1, 1, true, false);

        try {
            EventLoopGroup clientGroup = loops.onClient(false);
            EventLoopGroup serverGroup = loops.onServer(false);
            EventLoopGroup selectorGroup = loops.onServerSelect(false);
            Class<? extends SocketChannel> channelClass = loops.onChannelClass(SocketChannel.class, clientGroup);

            assertThat(clientGroup).isNotNull();
            assertThat(serverGroup).isNotNull();
            assertThat(selectorGroup).isNotNull();
            assertThat(channelClass).isEqualTo(NioSocketChannel.class);
            assertThat(loops.daemon()).isFalse();
        } finally {
            loops.disposeLater(Duration.ZERO, TIMEOUT).block(TIMEOUT);
        }
    }

    @Test
    void proxyProviderBuildsHttpProxyAndHonorsNonProxyHosts() {
        ProxyProvider proxyProvider = ProxyProvider.builder()
                .type(ProxyProvider.Proxy.HTTP)
                .host(LOCALHOST)
                .port(8888)
                .nonProxyHosts("localhost|127\\.0\\.0\\.1")
                .username("user")
                .password(username -> username + "-password")
                .connectTimeoutMillis(1_000)
                .build();

        assertThat(proxyProvider.getType()).isEqualTo(ProxyProvider.Proxy.HTTP);
        assertThat(proxyProvider.getAddress().get().getHostString()).isEqualTo(LOCALHOST);
        assertThat(proxyProvider.getAddress().get().getPort()).isEqualTo(8888);
        assertThat(proxyProvider.shouldProxy(InetSocketAddress.createUnresolved("example.com", 80))).isTrue();
        assertThat(proxyProvider.shouldProxy(InetSocketAddress.createUnresolved("localhost", 80))).isFalse();
        assertThat(proxyProvider.newProxyHandler()).isInstanceOf(HttpProxyHandler.class);
        assertThat(proxyProvider.toString()).contains("HTTP");
    }

    @Test
    void nameResolverProviderRetainsDnsConfiguration() {
        NameResolverProvider provider = NameResolverProvider.builder()
                .cacheMaxTimeToLive(Duration.ofSeconds(5))
                .cacheMinTimeToLive(Duration.ofSeconds(1))
                .cacheNegativeTimeToLive(Duration.ofMillis(500))
                .completeOncePreferredResolved(true)
                .disableOptionalRecord(true)
                .disableRecursionDesired(true)
                .maxPayloadSize(2_048)
                .maxQueriesPerResolve(3)
                .ndots(1)
                .queryTimeout(Duration.ofSeconds(2))
                .roundRobinSelection(true)
                .searchDomains(Arrays.asList("example.com", "local"))
                .bindAddressSupplier(() -> InetSocketAddress.createUnresolved(LOCALHOST, 0))
                .build();

        assertThat(provider.cacheMaxTimeToLive()).isEqualTo(Duration.ofSeconds(5));
        assertThat(provider.cacheMinTimeToLive()).isEqualTo(Duration.ofSeconds(1));
        assertThat(provider.cacheNegativeTimeToLive()).isEqualTo(Duration.ofMillis(500));
        assertThat(provider.isCompleteOncePreferredResolved()).isTrue();
        assertThat(provider.isDisableOptionalRecord()).isTrue();
        assertThat(provider.isDisableRecursionDesired()).isTrue();
        assertThat(provider.isRoundRobinSelection()).isTrue();
        assertThat(provider.maxPayloadSize()).isEqualTo(2_048);
        assertThat(provider.maxQueriesPerResolve()).isEqualTo(3);
        assertThat(provider.ndots()).isEqualTo(1);
        assertThat(provider.queryTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(provider.searchDomains()).containsExactly("example.com", "local");
        assertThat(provider.bindAddressSupplier().get()).isEqualTo(InetSocketAddress.createUnresolved(LOCALHOST, 0));
    }

    @Test
    void reactorNettyUtilitiesFormatChannelsAndReleaseByteBufs() {
        ByteBuf buffer = Unpooled.copiedBuffer("reactor-netty", StandardCharsets.UTF_8);

        try {
            String dump = ReactorNetty.toPrettyHexDump(buffer);

            assertThat(dump).contains("00000000").contains("reactor-netty");
            assertThat(buffer.refCnt()).isEqualTo(1);
            assertThatCode(() -> ReactorNetty.safeRelease("not-a-byte-buf")).doesNotThrowAnyException();
        } finally {
            ReactorNetty.safeRelease(buffer);
        }

        assertThat(buffer.refCnt()).isZero();
    }

    private static final class UnpooledByteBufAllocatorHolder {
        private static final ByteBufAllocator ALLOCATOR = UnpooledByteBufAllocator.DEFAULT;
    }
}
