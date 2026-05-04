/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_projectreactor_netty.reactor_netty_core;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.ByteBufMono;
import reactor.netty.Connection;
import reactor.netty.ConnectionObserver;
import reactor.netty.DisposableServer;
import reactor.netty.FutureMono;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;
import reactor.netty.transport.NameResolverProvider;
import reactor.netty.transport.ProxyProvider;
import reactor.netty.udp.UdpClient;
import reactor.netty.udp.UdpServer;

import java.io.InputStream;
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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class Reactor_netty_coreTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String LOOPBACK = "127.0.0.1";

    @TempDir
    private Path tempDir;

    @Test
    void tcpServerAndClientExchangeDataAndInvokeConnectionCallbacks() {
        AtomicInteger acceptedConnections = new AtomicInteger();
        AtomicBoolean clientConnected = new AtomicBoolean();
        CountDownLatch clientDisposed = new CountDownLatch(1);
        Queue<ConnectionObserver.State> observedStates = new ConcurrentLinkedQueue<>();
        ChannelHandler customHandler = new ChannelInboundHandlerAdapter();
        ConnectionProvider provider = ConnectionProvider.builder("reactor-netty-core-test")
                .maxConnections(1)
                .pendingAcquireTimeout(Duration.ofSeconds(2))
                .build();
        LoopResources loops = LoopResources.create("reactor-netty-tcp-test", 1, true);
        DisposableServer server = null;
        Connection client = null;

        try {
            server = TcpServer.create()
                    .host(LOOPBACK)
                    .runOn(loops, false)
                    .port(0)
                    .doOnConnection(connection -> {
                        acceptedConnections.incrementAndGet();
                        connection.addHandlerLast(new LineBasedFrameDecoder(128));
                    })
                    .handle((inbound, outbound) -> outbound.sendString(
                            inbound.receive()
                                    .asString(StandardCharsets.UTF_8)
                                    .map(value -> value.toUpperCase(Locale.ROOT) + "\n"),
                            StandardCharsets.UTF_8))
                    .bindNow(TIMEOUT);

            client = TcpClient.create(provider)
                    .host(LOOPBACK)
                    .runOn(loops, false)
                    .port(server.port())
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                    .observe((connection, newState) -> observedStates.add(newState))
                    .doOnConnected(connection -> {
                        clientConnected.set(true);
                        connection.markPersistent(false);
                        connection.addHandlerLast(new LineBasedFrameDecoder(128));
                        connection.addHandlerLast("customTestHandler", customHandler);
                        connection.onDispose(clientDisposed::countDown);
                    })
                    .connectNow(TIMEOUT);

            client.outbound()
                    .sendString(Mono.just("reactor-netty\n"), StandardCharsets.UTF_8)
                    .then()
                    .block(TIMEOUT);
            String response = client.inbound()
                    .receive()
                    .asString(StandardCharsets.UTF_8)
                    .next()
                    .block(TIMEOUT);

            assertThat(response).isEqualTo("REACTOR-NETTY");
            assertThat(acceptedConnections).hasValue(1);
            assertThat(clientConnected).isTrue();
            assertThat(client.isPersistent()).isFalse();
            assertThat(client.channel().pipeline().get("customTestHandler")).isSameAs(customHandler);
            assertThat(observedStates)
                    .contains(ConnectionObserver.State.CONNECTED, ConnectionObserver.State.CONFIGURED);
        } finally {
            if (client != null) {
                client.disposeNow(TIMEOUT);
            }
            if (server != null) {
                server.disposeNow(TIMEOUT);
            }
            provider.disposeLater().block(TIMEOUT);
            loops.disposeLater(Duration.ofMillis(100), Duration.ofSeconds(2)).block(TIMEOUT);
        }

        assertThat(clientDisposed.getCount()).isZero();
    }

    @Test
    void connectionInvokesIdleCallbacksWhenNoTrafficOccurs() throws Exception {
        CountDownLatch readIdle = new CountDownLatch(1);
        CountDownLatch writeIdle = new CountDownLatch(1);
        LoopResources loops = LoopResources.create("reactor-netty-idle-test", 1, true);
        DisposableServer server = null;
        Connection client = null;

        try {
            server = TcpServer.create()
                    .host(LOOPBACK)
                    .runOn(loops, false)
                    .port(0)
                    .doOnConnection(connection -> {
                        connection.onReadIdle(100, readIdle::countDown);
                        connection.onWriteIdle(100, writeIdle::countDown);
                    })
                    .handle((inbound, outbound) -> inbound.receive().then())
                    .bindNow(TIMEOUT);

            client = TcpClient.create()
                    .host(LOOPBACK)
                    .runOn(loops, false)
                    .port(server.port())
                    .connectNow(TIMEOUT);

            assertThat(readIdle.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            assertThat(writeIdle.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            if (client != null) {
                client.disposeNow(TIMEOUT);
            }
            if (server != null) {
                server.disposeNow(TIMEOUT);
            }
            loops.disposeLater(Duration.ofMillis(100), Duration.ofSeconds(2)).block(TIMEOUT);
        }
    }

    @Test
    void udpServerReceivesDatagramsFromClient() throws Exception {
        CountDownLatch receivedDatagram = new CountDownLatch(1);
        Queue<String> receivedMessages = new ConcurrentLinkedQueue<>();
        LoopResources loops = LoopResources.create("reactor-netty-udp-test", 1, true);
        Connection server = null;
        Connection client = null;

        try {
            server = UdpServer.create()
                    .host(LOOPBACK)
                    .runOn(loops, false)
                    .port(0)
                    .handle((inbound, outbound) -> inbound.receive()
                            .asString(StandardCharsets.UTF_8)
                            .doOnNext(message -> {
                                receivedMessages.add(message);
                                receivedDatagram.countDown();
                            })
                            .then())
                    .bindNow(TIMEOUT);

            int serverPort = ((InetSocketAddress) server.address()).getPort();
            client = UdpClient.create()
                    .host(LOOPBACK)
                    .runOn(loops, false)
                    .port(serverPort)
                    .connectNow(TIMEOUT);

            client.outbound()
                    .sendString(Mono.just("udp-payload"), StandardCharsets.UTF_8)
                    .then()
                    .block(TIMEOUT);

            assertThat(receivedDatagram.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            assertThat(receivedMessages).containsExactly("udp-payload");
        } finally {
            if (client != null) {
                client.disposeNow(TIMEOUT);
            }
            if (server != null) {
                server.disposeNow(TIMEOUT);
            }
            loops.disposeLater(Duration.ofMillis(100), Duration.ofSeconds(2)).block(TIMEOUT);
        }
    }

    @Test
    void byteBufPublishersConvertStringsFilesByteArraysAndInputStreams() throws Exception {
        String fileContent = "alpha\nbeta\ngamma";
        Path file = tempDir.resolve("reactor-netty-payload.txt");
        Files.writeString(file, fileContent, StandardCharsets.UTF_8);

        String fromPath = ByteBufFlux.fromPath(file, 5, ByteBufAllocator.DEFAULT)
                .asString(StandardCharsets.UTF_8)
                .collectList()
                .map(parts -> String.join("", parts))
                .block(TIMEOUT);
        String aggregated = ByteBufFlux.fromString(
                        Flux.just("reactor", "-", "netty"),
                        StandardCharsets.UTF_8,
                        ByteBufAllocator.DEFAULT)
                .aggregate()
                .asString(StandardCharsets.UTF_8)
                .block(TIMEOUT);
        List<byte[]> byteArrays = ByteBufFlux.fromString(
                        Flux.just("ab", "cd"),
                        StandardCharsets.UTF_8,
                        ByteBufAllocator.DEFAULT)
                .asByteArray()
                .collectList()
                .block(TIMEOUT);
        ByteBuffer byteBuffer = ByteBufMono.fromString(
                        Mono.just("buffer"),
                        StandardCharsets.UTF_8,
                        ByteBufAllocator.DEFAULT)
                .asByteBuffer()
                .block(TIMEOUT);

        byte[] inputStreamBytes;
        try (InputStream inputStream = ByteBufMono.fromString(
                        Mono.just("stream"),
                        StandardCharsets.UTF_8,
                        ByteBufAllocator.DEFAULT)
                .asInputStream()
                .block(TIMEOUT)) {
            inputStreamBytes = inputStream.readAllBytes();
        }

        assertThat(fromPath).isEqualTo(fileContent);
        assertThat(aggregated).isEqualTo("reactor-netty");
        assertThat(byteArrays).extracting(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .containsExactly("ab", "cd");
        assertThat(StandardCharsets.UTF_8.decode(byteBuffer).toString()).isEqualTo("buffer");
        assertThat(new String(inputStreamBytes, StandardCharsets.UTF_8)).isEqualTo("stream");
    }

    @Test
    void connectionProviderBuilderConfiguresGlobalAndHostSpecificPools() {
        SocketAddress remoteHost = InetSocketAddress.createUnresolved("example.org", 443);
        ConnectionProvider provider = ConnectionProvider.builder("configured-provider")
                .maxConnections(3)
                .pendingAcquireMaxCount(4)
                .pendingAcquireTimeout(Duration.ofSeconds(3))
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofSeconds(30))
                .lifo()
                .forRemoteHost(remoteHost, hostSpec -> hostSpec.maxConnections(1)
                        .pendingAcquireMaxCount(2))
                .build();

        try {
            assertThat(provider.name()).isEqualTo("configured-provider");
            assertThat(provider.maxConnections()).isEqualTo(3);
            assertThat(provider.maxConnectionsPerHost()).containsEntry(remoteHost, 1);

            ConnectionProvider mutated = provider.mutate()
                    .name("mutated-provider")
                    .maxConnections(2)
                    .build();
            try {
                assertThat(mutated.name()).isEqualTo("mutated-provider");
                assertThat(mutated.maxConnections()).isEqualTo(2);
                assertThat(mutated.maxConnectionsPerHost()).containsEntry(remoteHost, 1);
            } finally {
                mutated.disposeLater().block(TIMEOUT);
            }
        } finally {
            provider.disposeLater().block(TIMEOUT);
        }
    }

    @Test
    void loopResourcesCreateEventLoopGroupsAndDisposeGracefully() {
        LoopResources loops = LoopResources.create("reactor-netty-loop-test", 1, 1, true, true);

        try {
            EventLoopGroup clientGroup = loops.onClient(false);
            EventLoopGroup serverGroup = loops.onServer(false);
            EventLoopGroup selectGroup = loops.onServerSelect(false);

            assertThat(clientGroup).isNotNull();
            assertThat(serverGroup).isNotNull();
            assertThat(selectGroup).isNotNull();
        } finally {
            loops.disposeLater(Duration.ofMillis(100), Duration.ofSeconds(2)).block(TIMEOUT);
        }
    }

    @Test
    void proxyAndNameResolverProvidersExposeConfiguredPublicOptions() {
        ProxyProvider proxy = ProxyProvider.builder()
                .type(ProxyProvider.Proxy.HTTP)
                .host("192.0.2.1")
                .port(8080)
                .username("user")
                .password(username -> username + "-password")
                .nonProxyHosts("localhost|127\\..*")
                .connectTimeoutMillis(750)
                .build();
        NameResolverProvider resolver = NameResolverProvider.builder()
                .cacheMaxTimeToLive(Duration.ofSeconds(30))
                .cacheMinTimeToLive(Duration.ofSeconds(1))
                .cacheNegativeTimeToLive(Duration.ZERO)
                .completeOncePreferredResolved(true)
                .disableOptionalRecord(true)
                .disableRecursionDesired(true)
                .maxPayloadSize(2048)
                .maxQueriesPerResolve(2)
                .ndots(1)
                .queryTimeout(Duration.ofSeconds(2))
                .roundRobinSelection(true)
                .searchDomains(Arrays.asList("example.org", "internal.example.org"))
                .build();

        InetSocketAddress proxyAddress = proxy.getAddress().get();

        assertThat(proxy.getType()).isEqualTo(ProxyProvider.Proxy.HTTP);
        assertThat(proxyAddress.getHostString()).isEqualTo("192.0.2.1");
        assertThat(proxyAddress.getPort()).isEqualTo(8080);
        assertThat(proxy.shouldProxy(InetSocketAddress.createUnresolved("service.example.org", 443)))
                .isTrue();
        assertThat(proxy.shouldProxy(InetSocketAddress.createUnresolved("localhost", 8080)))
                .isFalse();
        assertThat(proxy.newProxyHandler()).isInstanceOf(HttpProxyHandler.class);

        assertThat(resolver.cacheMaxTimeToLive()).isEqualTo(Duration.ofSeconds(30));
        assertThat(resolver.cacheMinTimeToLive()).isEqualTo(Duration.ofSeconds(1));
        assertThat(resolver.cacheNegativeTimeToLive()).isEqualTo(Duration.ZERO);
        assertThat(resolver.isCompleteOncePreferredResolved()).isTrue();
        assertThat(resolver.isDisableOptionalRecord()).isTrue();
        assertThat(resolver.isDisableRecursionDesired()).isTrue();
        assertThat(resolver.maxPayloadSize()).isEqualTo(2048);
        assertThat(resolver.maxQueriesPerResolve()).isEqualTo(2);
        assertThat(resolver.ndots()).isEqualTo(1);
        assertThat(resolver.queryTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(resolver.isRoundRobinSelection()).isTrue();
        assertThat(resolver.searchDomains()).containsExactly("example.org", "internal.example.org");
    }

    @Test
    void futureMonoAdaptsSucceededAndDeferredNettyFutures() {
        FutureMono.from(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null)).block(TIMEOUT);
        FutureMono.deferFuture(() -> ImmediateEventExecutor.INSTANCE.newSucceededFuture(null)).block(TIMEOUT);
    }
}
