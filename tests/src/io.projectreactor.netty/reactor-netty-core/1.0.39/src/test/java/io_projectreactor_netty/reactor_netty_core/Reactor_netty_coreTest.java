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
import io.netty.handler.ssl.SslContextBuilder;
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
import reactor.netty.tcp.TcpSslContextSpec;
import reactor.netty.transport.NameResolverProvider;
import reactor.netty.transport.ProxyProvider;
import reactor.netty.udp.UdpClient;
import reactor.netty.udp.UdpServer;

import java.io.ByteArrayInputStream;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class Reactor_netty_coreTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String LOCALHOST = "127.0.0.1";
    private static final String TEST_CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIDJTCCAg2gAwIBAgIUUDHuMAJtvwglXLM7Yx4Px8DMWtIwDQYJKoZIhvcNAQEL
            BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDUwMjE2MDA1NFoXDTM2MDQy
            OTE2MDA1NFowFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF
            AAOCAQ8AMIIBCgKCAQEA31hq2BQWgjQm6GsjrtoeTs/XqatpFrUFx37UuK/8umTT
            oNDyoLl/YZZ02ce9KLotfITtsI12ZtpsP184arjc3HC3WSUNo6P5jgcjbI0vss1Y
            rib9/6dg4SCefOCj3xYVEUlHpop0rnUP9J05alCaFf/yD2b2djVJXd5apUl8ZG2u
            VNdeulevhYGLdlnppuidSWVe8Y8QxdYBDzotifRp0/VxIOXzy8r5KgouALGGoUS8
            UYFvSQ8kc1YFNZgNOrsBRylP88aLxqsRe4TiL/LsWOfbN0ezh958HGH1hpL0oN/I
            ufRq61VAQmf3Np8K9/GKoaxe8FTuVXx4rth6RWj/lQIDAQABo28wbTAdBgNVHQ4E
            FgQUmOdtzKNjpu3WD2E0+nNHRS5HIIEwHwYDVR0jBBgwFoAUmOdtzKNjpu3WD2E0
            +nNHRS5HIIEwDwYDVR0TAQH/BAUwAwEB/zAaBgNVHREEEzARgglsb2NhbGhvc3SH
            BH8AAAEwDQYJKoZIhvcNAQELBQADggEBAFpoiuh1YJXHVkqoOEs85koV3g/bcvN2
            sj8G6IcHMSGOEiy+jB35XX5YQ1Ik0vUYY3Q8xCPwYOy86KPX7CX/aCIsVAwnU1ua
            NDDT5LOVUnxWC1z6kqNkDTlYobYZbowtVYXPgmwBhHxdaz3iXQgfoC8F+pH6q7rB
            onuCtS8Qnz0R0j+HGOwDkLBtGipYZgBqFesUiwoDQKQUsOxzDtDr8CshAklotAbs
            zlOTnfNN9QF7Ixof/zD/xzospjOMU2XL+bgIYVrt3KnC8+4MH1esNwvVaKSneSTc
            P8uGlHPBh+CyiH9rFgnDP8AtjHeNavte39XTO5m8P+uMSnE9lFp1P48=
            -----END CERTIFICATE-----
            """;
    private static final String TEST_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDfWGrYFBaCNCbo
            ayOu2h5Oz9epq2kWtQXHftS4r/y6ZNOg0PKguX9hlnTZx70oui18hO2wjXZm2mw/
            XzhquNzccLdZJQ2jo/mOByNsjS+yzViuJv3/p2DhIJ584KPfFhURSUeminSudQ/0
            nTlqUJoV//IPZvZ2NUld3lqlSXxkba5U1166V6+FgYt2Wemm6J1JZV7xjxDF1gEP
            Oi2J9GnT9XEg5fPLyvkqCi4AsYahRLxRgW9JDyRzVgU1mA06uwFHKU/zxovGqxF7
            hOIv8uxY59s3R7OH3nwcYfWGkvSg38i59GrrVUBCZ/c2nwr38YqhrF7wVO5VfHiu
            2HpFaP+VAgMBAAECggEAD/6/jCEIKaP9g1ik8hFJ6WAGNG8DtC4br1lcd6uW4Gx3
            IYXFUpBmWIFTdgNRfziyKEBG24WODLio1vMFUdNScfEamGZIb0c/iJXnSg9kcpw1
            NSeyuhvtGsJgx1W5NrFYqefDG2DAEXxtu5mE8qG6H0g4uDSqAvY2/mN9v2efYnu8
            HLhLJHn2aSWo0aIij1z4Jyj5zK44OUPAoYSA7pC/bOXpV/v7kgii8XUikHyO57yj
            +7g0caG3lBtAIeYZC0P69ZrdxlGLu+2DLaLWiiwV2B+MoENJ1OJBKR4As2Z878Lp
            WGv+qXb/m5ZstlmTmcJphqVVzZeybh0w6K6JB3DGwwKBgQD7lynG0YhikESAyhnz
            nPyaGwioYRCtjsaCXB2umPA8iLM3+45zNTfGABxPmfUDi+bEoKE7RaAAiuTuDbu9
            YApRAESu2taGzEFUQICb368NpBHht/NP9iD0869Pq22FRbWm02UzlwL3J58XDkL4
            ph3tJ5LIDkn3hFQyAVO/ZyMNZwKBgQDjQoYjf7LcwYcBKpM9DfTf91kWlGt3V63J
            MP2LkITfc9IYiNax3gsBhihygfJoLWsSwIv6pH4L5CRjpRR3tKwKROJtRV+A5wlh
            3K7LngmP4S9+gxIPEm5bCk7l0EgiMNHgDGTiwwk4dtM494ALs5hdhYHTKFJ4LWJB
            LQRZrEtxowKBgQCm6HQIuH14lik8H9fzrFRQkFrAChUcbzn2xdHTQRcvsajkHPk2
            KTolG3GsxYCsp6WjEMWmItyxP3P9EhNY4Vw2vKzUK85igyNcF6a6wjzKGezbCERc
            6faXSwslGZ+A6OxIDrp27VpESX7bttRrTRlReg2AtyoPETUiL4s10eCJRQKBgQCI
            QTFlhUG7A7kq5NjkiUKhKY7bb99C7Wm/r8TEccCIrMtxdFGs0OEuZ75GcUziUyDY
            XGNQwmDkRkPfDnHIF6Xyfjx3oVlSUrMYXpTadgVro2qzYmhoaveJVBPby9YD0dtz
            hlrSbndPyEZ56EJ4QZR/tfURoiJX9XXsd84c6aVOGwKBgQCqnVvH+udpfPsa5t7U
            Vze78pMIFRxfLbylyEFoDhck5t+5S3VbAS4xxNMMLyfxsRwDPGtnJOSI3IRF5CFp
            X52M+E2PVc+Dg3IcQSsdWXWGevSsJJZql1EvTUV7RYj9iHA25Yym9etx9KrafEzp
            pj0v5FbqpZaDAniSensElxxFKw==
            -----END PRIVATE KEY-----
            """;

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
    void tcpClientAndServerCommunicateOverTls() throws Exception {
        LoopResources loops = LoopResources.create("rn-tls-test", 1, true);
        CountDownLatch responseReceived = new CountDownLatch(1);
        AtomicReference<String> response = new AtomicReference<>();
        DisposableServer server = null;
        Connection client = null;

        try {
            server = TcpServer.create()
                    .host(LOCALHOST)
                    .port(0)
                    .runOn(loops)
                    .secure(spec -> spec.sslContext(TcpSslContextSpec.forServer(
                            certificateStream(), privateKeyStream())))
                    .handle((inbound, outbound) -> inbound.receive()
                            .asString(StandardCharsets.UTF_8)
                            .take(1)
                            .flatMap(message -> outbound.sendString(
                                    Mono.just("secure:" + message),
                                    StandardCharsets.UTF_8).then()))
                    .bindNow(TIMEOUT);

            client = TcpClient.newConnection()
                    .host(LOCALHOST)
                    .port(server.port())
                    .runOn(loops)
                    .secure(spec -> spec.sslContext(SslContextBuilder.forClient()
                            .trustManager(certificateStream())))
                    .handle((inbound, outbound) -> outbound.sendString(Mono.just("ping"), StandardCharsets.UTF_8)
                            .then(inbound.receive()
                                    .asString(StandardCharsets.UTF_8)
                                    .next()
                                    .doOnNext(message -> {
                                        response.set(message);
                                        responseReceived.countDown();
                                    })
                                    .then()))
                    .connectNow(TIMEOUT);

            assertThat(responseReceived.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(response.get()).isEqualTo("secure:ping");
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

    private static InputStream certificateStream() {
        return new ByteArrayInputStream(TEST_CERTIFICATE.getBytes(StandardCharsets.US_ASCII));
    }

    private static InputStream privateKeyStream() {
        return new ByteArrayInputStream(TEST_PRIVATE_KEY.getBytes(StandardCharsets.US_ASCII));
    }

    private static final class UnpooledByteBufAllocatorHolder {
        private static final ByteBufAllocator ALLOCATOR = UnpooledByteBufAllocator.DEFAULT;
    }
}
