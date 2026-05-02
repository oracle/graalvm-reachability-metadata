/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_native_epoll;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollDatagramChannelConfig;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollDomainSocketChannelConfig;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollMode;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannelConfig;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.epoll.EpollSocketChannelConfig;
import io.netty.channel.epoll.EpollTcpInfo;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.DomainSocketReadMode;
import io.netty.channel.unix.PeerCredentials;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

public class Netty_transport_native_epollTest {
    @Test
    void epollAvailabilityContractIsSelfConsistent() {
        boolean available = Epoll.isAvailable();
        Throwable cause = Epoll.unavailabilityCause();

        assertThat(available).isEqualTo(cause == null);
        if (available) {
            assertThatCode(Epoll::ensureAvailability).doesNotThrowAnyException();
            return;
        }

        assertThat(cause).isNotNull();
        Throwable thrown = catchThrowable(Epoll::ensureAvailability);
        assertThat(thrown).isInstanceOf(UnsatisfiedLinkError.class);
        assertThat(thrown.getCause()).isSameAs(cause);
    }

    @Test
    void epollPublicTypesRespectTransportAvailability() {
        assertChannelConstructorMatchesAvailability(EpollSocketChannel::new);
        assertChannelConstructorMatchesAvailability(EpollServerSocketChannel::new);
        assertChannelConstructorMatchesAvailability(EpollDatagramChannel::new);
        assertChannelConstructorMatchesAvailability(EpollDomainSocketChannel::new);
        assertChannelConstructorMatchesAvailability(EpollServerDomainSocketChannel::new);
        assertEventLoopGroupConstructorMatchesAvailability(EpollEventLoopGroup::new);
    }

    @Test
    void epollChannelOptionsAreRegisteredInTheConstantPool() {
        List<ChannelOption<?>> options = List.of(
                EpollChannelOption.TCP_CORK,
                EpollChannelOption.TCP_NOTSENT_LOWAT,
                EpollChannelOption.TCP_KEEPIDLE,
                EpollChannelOption.TCP_KEEPINTVL,
                EpollChannelOption.TCP_KEEPCNT,
                EpollChannelOption.TCP_USER_TIMEOUT,
                EpollChannelOption.IP_FREEBIND,
                EpollChannelOption.IP_TRANSPARENT,
                EpollChannelOption.IP_RECVORIGDSTADDR,
                EpollChannelOption.TCP_FASTOPEN,
                EpollChannelOption.TCP_FASTOPEN_CONNECT,
                EpollChannelOption.TCP_DEFER_ACCEPT,
                EpollChannelOption.TCP_QUICKACK,
                EpollChannelOption.SO_BUSY_POLL,
                EpollChannelOption.EPOLL_MODE,
                EpollChannelOption.TCP_MD5SIG,
                EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE
        );

        assertThat(options).doesNotContainNull();
        assertThat(new LinkedHashSet<>(options)).hasSize(options.size());

        for (ChannelOption<?> option : options) {
            assertThat(option.name()).isNotBlank();
            assertThat(ChannelOption.valueOf(option.name())).isSameAs(option);
        }
    }

    @Test
    void epollModesExposeStableEnumSemantics() {
        assertThat(EpollMode.valueOf("EDGE_TRIGGERED")).isSameAs(EpollMode.EDGE_TRIGGERED);
        assertThat(EpollMode.valueOf("LEVEL_TRIGGERED")).isSameAs(EpollMode.LEVEL_TRIGGERED);
        assertThat(EpollMode.values()).containsExactly(EpollMode.EDGE_TRIGGERED, EpollMode.LEVEL_TRIGGERED);
    }

    @Test
    void defaultTcpInfoContainsKernelCounterSnapshotAccessors() {
        EpollTcpInfo tcpInfo = new EpollTcpInfo();

        assertThat(tcpInfo.state()).isZero();
        assertThat(tcpInfo.caState()).isZero();
        assertThat(tcpInfo.retransmits()).isZero();
        assertThat(tcpInfo.probes()).isZero();
        assertThat(tcpInfo.backoff()).isZero();
        assertThat(tcpInfo.options()).isZero();
        assertThat(tcpInfo.sndWscale()).isZero();
        assertThat(tcpInfo.rcvWscale()).isZero();
        assertThat(tcpInfo.rto()).isZero();
        assertThat(tcpInfo.ato()).isZero();
        assertThat(tcpInfo.sndMss()).isZero();
        assertThat(tcpInfo.rcvMss()).isZero();
        assertThat(tcpInfo.unacked()).isZero();
        assertThat(tcpInfo.sacked()).isZero();
        assertThat(tcpInfo.lost()).isZero();
        assertThat(tcpInfo.retrans()).isZero();
        assertThat(tcpInfo.fackets()).isZero();
        assertThat(tcpInfo.lastDataSent()).isZero();
        assertThat(tcpInfo.lastAckSent()).isZero();
        assertThat(tcpInfo.lastDataRecv()).isZero();
        assertThat(tcpInfo.lastAckRecv()).isZero();
        assertThat(tcpInfo.pmtu()).isZero();
        assertThat(tcpInfo.rcvSsthresh()).isZero();
        assertThat(tcpInfo.rtt()).isZero();
        assertThat(tcpInfo.rttvar()).isZero();
        assertThat(tcpInfo.sndSsthresh()).isZero();
        assertThat(tcpInfo.sndCwnd()).isZero();
        assertThat(tcpInfo.advmss()).isZero();
        assertThat(tcpInfo.reordering()).isZero();
        assertThat(tcpInfo.rcvRtt()).isZero();
        assertThat(tcpInfo.rcvSpace()).isZero();
        assertThat(tcpInfo.totalRetrans()).isZero();
    }

    @Test
    void epollChannelConfigurationRoundTripsPortableOptionsWhenAvailable() {
        if (!Epoll.isAvailable()) {
            assertThatThrownBy(EpollSocketChannel::new).isInstanceOf(LinkageError.class);
            return;
        }

        EpollSocketChannel socketChannel = new EpollSocketChannel();
        EpollServerSocketChannel serverSocketChannel = new EpollServerSocketChannel();
        EpollDatagramChannel datagramChannel = new EpollDatagramChannel();
        EpollDomainSocketChannel domainSocketChannel = new EpollDomainSocketChannel();
        try {
            EpollSocketChannelConfig socketConfig = socketChannel.config();
            assertThat(socketConfig.getOptions()).containsKeys(
                    EpollChannelOption.TCP_CORK,
                    EpollChannelOption.TCP_QUICKACK,
                    EpollChannelOption.EPOLL_MODE
            );

            socketConfig.setTcpCork(false);
            assertThat(socketConfig.isTcpCork()).isFalse();
            socketConfig.setTcpQuickAck(true);
            assertThat(socketConfig.isTcpQuickAck()).isTrue();
            socketConfig.setEpollMode(EpollMode.LEVEL_TRIGGERED);
            assertThat(socketConfig.getEpollMode()).isSameAs(EpollMode.LEVEL_TRIGGERED);
            socketConfig.setOption(EpollChannelOption.EPOLL_MODE, EpollMode.EDGE_TRIGGERED);
            assertThat(socketConfig.getOption(EpollChannelOption.EPOLL_MODE)).isSameAs(EpollMode.EDGE_TRIGGERED);

            EpollServerSocketChannelConfig serverConfig = serverSocketChannel.config();
            serverConfig.setTcpDeferAccept(0);
            assertThat(serverConfig.getTcpDeferAccept()).isZero();

            EpollDatagramChannelConfig datagramConfig = datagramChannel.config();
            datagramConfig.setMaxDatagramPayloadSize(2048);
            assertThat(datagramConfig.getMaxDatagramPayloadSize()).isEqualTo(2048);
            assertThat(datagramConfig.getOption(EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE)).isEqualTo(2048);

            EpollDomainSocketChannelConfig domainConfig = domainSocketChannel.config();
            domainConfig.setReadMode(DomainSocketReadMode.FILE_DESCRIPTORS);
            assertThat(domainConfig.getReadMode()).isSameAs(DomainSocketReadMode.FILE_DESCRIPTORS);
            domainConfig.setReadMode(DomainSocketReadMode.BYTES);
            assertThat(domainConfig.getReadMode()).isSameAs(DomainSocketReadMode.BYTES);
        } finally {
            domainSocketChannel.close().syncUninterruptibly();
            datagramChannel.close().syncUninterruptibly();
            serverSocketChannel.close().syncUninterruptibly();
            socketChannel.close().syncUninterruptibly();
        }
    }

    @Test
    void epollTcpChannelsCanExchangeBytesWhenAvailable() throws Exception {
        if (!Epoll.isAvailable()) {
            assertThatThrownBy(EpollEventLoopGroup::new).isInstanceOf(LinkageError.class);
            return;
        }

        EventLoopGroup bossGroup = new EpollEventLoopGroup(1);
        EventLoopGroup workerGroup = new EpollEventLoopGroup(1);
        EventLoopGroup clientGroup = new EpollEventLoopGroup(1);
        Channel serverChannel = null;
        try {
            CountDownLatch responseReceived = new CountDownLatch(1);
            AtomicReference<String> response = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(EpollServerSocketChannel.class)
                    .childHandler(new EchoServerInitializer(failure));
            serverChannel = serverBootstrap.bind(new InetSocketAddress("127.0.0.1", 0)).syncUninterruptibly().channel();
            int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

            Bootstrap clientBootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channel(EpollSocketChannel.class)
                    .handler(new EchoClientInitializer(response, responseReceived, failure));
            Channel clientChannel = clientBootstrap.connect("127.0.0.1", port).syncUninterruptibly().channel();
            clientChannel.writeAndFlush(Unpooled.copiedBuffer("ping", CharsetUtil.UTF_8)).syncUninterruptibly();
            clientChannel.closeFuture().syncUninterruptibly();

            assertThat(responseReceived.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(response.get()).isEqualTo("echo:ping");
        } finally {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            clientGroup.shutdownGracefully().syncUninterruptibly();
            workerGroup.shutdownGracefully().syncUninterruptibly();
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void epollTcpChannelsSupportHalfClosureWhenAvailable() throws Exception {
        if (!Epoll.isAvailable()) {
            assertThatThrownBy(EpollEventLoopGroup::new).isInstanceOf(LinkageError.class);
            return;
        }

        EventLoopGroup bossGroup = new EpollEventLoopGroup(1);
        EventLoopGroup workerGroup = new EpollEventLoopGroup(1);
        EventLoopGroup clientGroup = new EpollEventLoopGroup(1);
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            CountDownLatch responseReceived = new CountDownLatch(1);
            CountDownLatch inputShutdownReceived = new CountDownLatch(1);
            AtomicReference<String> request = new AtomicReference<>();
            AtomicReference<String> response = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(EpollServerSocketChannel.class)
                    .childOption(ChannelOption.ALLOW_HALF_CLOSURE, true)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                    request.set(msg.toString(CharsetUtil.UTF_8));
                                }

                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                    if (evt == ChannelInputShutdownEvent.INSTANCE) {
                                        inputShutdownReceived.countDown();
                                        ctx.writeAndFlush(Unpooled.copiedBuffer(
                                                "ack:" + request.get(),
                                                CharsetUtil.UTF_8
                                        )).addListener(ChannelFutureListener.CLOSE);
                                        return;
                                    }

                                    ctx.fireUserEventTriggered(evt);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    failure.compareAndSet(null, cause);
                                    inputShutdownReceived.countDown();
                                    ctx.close();
                                }
                            });
                        }
                    });
            serverChannel = serverBootstrap.bind(new InetSocketAddress("127.0.0.1", 0)).syncUninterruptibly().channel();
            int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

            Bootstrap clientBootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channel(EpollSocketChannel.class)
                    .option(ChannelOption.ALLOW_HALF_CLOSURE, true)
                    .handler(new EchoClientInitializer(response, responseReceived, failure));
            clientChannel = clientBootstrap.connect("127.0.0.1", port).syncUninterruptibly().channel();
            clientChannel.writeAndFlush(Unpooled.copiedBuffer("half-close", CharsetUtil.UTF_8)).syncUninterruptibly();

            EpollSocketChannel epollClientChannel = (EpollSocketChannel) clientChannel;
            epollClientChannel.shutdownOutput().syncUninterruptibly();

            assertThat(epollClientChannel.isOutputShutdown()).isTrue();
            assertThat(epollClientChannel.isInputShutdown()).isFalse();
            assertThat(inputShutdownReceived.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(responseReceived.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(request.get()).isEqualTo("half-close");
            assertThat(response.get()).isEqualTo("ack:half-close");
        } finally {
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            clientGroup.shutdownGracefully().syncUninterruptibly();
            workerGroup.shutdownGracefully().syncUninterruptibly();
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void epollDomainSocketChannelsCanExchangeBytesWhenAvailable() throws Exception {
        if (!Epoll.isAvailable()) {
            assertThatThrownBy(EpollEventLoopGroup::new).isInstanceOf(LinkageError.class);
            return;
        }

        Path socketPath = Files.createTempFile("netty-epoll-domain-", ".sock");
        Files.deleteIfExists(socketPath);
        DomainSocketAddress socketAddress = new DomainSocketAddress(socketPath.toFile());
        EventLoopGroup serverGroup = new EpollEventLoopGroup(1);
        EventLoopGroup clientGroup = new EpollEventLoopGroup(1);
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            CountDownLatch responseReceived = new CountDownLatch(1);
            AtomicReference<String> response = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(serverGroup)
                    .channel(EpollServerDomainSocketChannel.class)
                    .childHandler(new EchoServerInitializer(failure));
            serverChannel = serverBootstrap.bind(socketAddress).syncUninterruptibly().channel();

            Bootstrap clientBootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channel(EpollDomainSocketChannel.class)
                    .handler(new EchoClientInitializer(response, responseReceived, failure));
            clientChannel = clientBootstrap.connect(socketAddress).syncUninterruptibly().channel();
            clientChannel.writeAndFlush(Unpooled.copiedBuffer("domain", CharsetUtil.UTF_8)).syncUninterruptibly();

            assertThat(responseReceived.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(response.get()).isEqualTo("echo:domain");
        } finally {
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            clientGroup.shutdownGracefully().syncUninterruptibly();
            serverGroup.shutdownGracefully().syncUninterruptibly();
            Files.deleteIfExists(socketPath);
        }
    }

    @Test
    void epollDomainSocketChannelsExposePeerCredentialsWhenAvailable() throws Exception {
        if (!Epoll.isAvailable()) {
            assertThatThrownBy(EpollEventLoopGroup::new).isInstanceOf(LinkageError.class);
            return;
        }

        Path socketPath = Files.createTempFile("netty-epoll-peer-", ".sock");
        Files.deleteIfExists(socketPath);
        DomainSocketAddress socketAddress = new DomainSocketAddress(socketPath.toFile());
        EventLoopGroup serverGroup = new EpollEventLoopGroup(1);
        EventLoopGroup clientGroup = new EpollEventLoopGroup(1);
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            CountDownLatch credentialsReceived = new CountDownLatch(1);
            AtomicReference<PeerCredentials> credentials = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(serverGroup)
                    .channel(EpollServerDomainSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    try {
                                        credentials.set(((EpollDomainSocketChannel) ctx.channel()).peerCredentials());
                                    } catch (IOException e) {
                                        failure.compareAndSet(null, e);
                                    } finally {
                                        credentialsReceived.countDown();
                                        ctx.close();
                                    }
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    failure.compareAndSet(null, cause);
                                    credentialsReceived.countDown();
                                    ctx.close();
                                }
                            });
                        }
                    });
            serverChannel = serverBootstrap.bind(socketAddress).syncUninterruptibly().channel();

            Bootstrap clientBootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channel(EpollDomainSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                        }
                    });
            clientChannel = clientBootstrap.connect(socketAddress).syncUninterruptibly().channel();
            clientChannel.closeFuture().syncUninterruptibly();

            assertThat(credentialsReceived.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            PeerCredentials peerCredentials = credentials.get();
            assertThat(peerCredentials).isNotNull();
            assertThat(peerCredentials.pid()).isPositive();
            assertThat(peerCredentials.uid()).isGreaterThanOrEqualTo(0);
            assertThat(peerCredentials.gids()).isNotNull();
            for (int gid : peerCredentials.gids()) {
                assertThat(gid).isGreaterThanOrEqualTo(0);
            }
        } finally {
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            clientGroup.shutdownGracefully().syncUninterruptibly();
            serverGroup.shutdownGracefully().syncUninterruptibly();
            Files.deleteIfExists(socketPath);
        }
    }

    @Test
    void epollDatagramChannelsCanExchangePacketsWhenAvailable() throws Exception {
        if (!Epoll.isAvailable()) {
            assertThatThrownBy(EpollEventLoopGroup::new).isInstanceOf(LinkageError.class);
            return;
        }

        EventLoopGroup serverGroup = new EpollEventLoopGroup(1);
        EventLoopGroup clientGroup = new EpollEventLoopGroup(1);
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            CountDownLatch responseReceived = new CountDownLatch(1);
            AtomicReference<String> response = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            Bootstrap serverBootstrap = new Bootstrap()
                    .group(serverGroup)
                    .channel(EpollDatagramChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            channel.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                                    String request = packet.content().toString(CharsetUtil.UTF_8);
                                    ctx.writeAndFlush(new DatagramPacket(
                                            Unpooled.copiedBuffer("datagram:" + request, CharsetUtil.UTF_8),
                                            packet.sender()
                                    ));
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    failure.compareAndSet(null, cause);
                                    ctx.close();
                                }
                            });
                        }
                    });
            serverChannel = serverBootstrap.bind(new InetSocketAddress("127.0.0.1", 0)).syncUninterruptibly().channel();
            InetSocketAddress serverAddress = (InetSocketAddress) serverChannel.localAddress();

            Bootstrap clientBootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channel(EpollDatagramChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            channel.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                                    response.set(packet.content().toString(CharsetUtil.UTF_8));
                                    responseReceived.countDown();
                                    ctx.close();
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    failure.compareAndSet(null, cause);
                                    responseReceived.countDown();
                                    ctx.close();
                                }
                            });
                        }
                    });
            clientChannel = clientBootstrap.bind(new InetSocketAddress("127.0.0.1", 0)).syncUninterruptibly().channel();
            clientChannel.writeAndFlush(new DatagramPacket(
                    Unpooled.copiedBuffer("ping", CharsetUtil.UTF_8),
                    serverAddress
            )).syncUninterruptibly();

            assertThat(responseReceived.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(response.get()).isEqualTo("datagram:ping");
        } finally {
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            clientGroup.shutdownGracefully().syncUninterruptibly();
            serverGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static void assertChannelConstructorMatchesAvailability(Supplier<? extends Channel> constructor) {
        if (Epoll.isAvailable()) {
            Channel channel = constructor.get();
            try {
                assertThat(channel.isOpen()).isTrue();
            } finally {
                channel.close().syncUninterruptibly();
            }
            return;
        }

        assertThatThrownBy(constructor::get).isInstanceOf(LinkageError.class);
    }

    private static void assertEventLoopGroupConstructorMatchesAvailability(
            Supplier<? extends EventLoopGroup> constructor
    ) {
        if (Epoll.isAvailable()) {
            EventLoopGroup group = constructor.get();
            try {
                assertThat(group.isShuttingDown()).isFalse();
            } finally {
                group.shutdownGracefully().syncUninterruptibly();
            }
            return;
        }

        assertThatThrownBy(constructor::get).isInstanceOf(LinkageError.class);
    }

    private static final class EchoServerInitializer extends ChannelInitializer<Channel> {
        private final AtomicReference<Throwable> failure;

        private EchoServerInitializer(AtomicReference<Throwable> failure) {
            this.failure = failure;
        }

        @Override
        protected void initChannel(Channel channel) {
            channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                    String request = msg.toString(CharsetUtil.UTF_8);
                    ctx.writeAndFlush(Unpooled.copiedBuffer("echo:" + request, CharsetUtil.UTF_8))
                            .addListener(ChannelFutureListener.CLOSE);
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    failure.compareAndSet(null, cause);
                    ctx.close();
                }
            });
        }
    }

    private static final class EchoClientInitializer extends ChannelInitializer<Channel> {
        private final AtomicReference<String> response;

        private final CountDownLatch responseReceived;

        private final AtomicReference<Throwable> failure;

        private EchoClientInitializer(
                AtomicReference<String> response,
                CountDownLatch responseReceived,
                AtomicReference<Throwable> failure
        ) {
            this.response = response;
            this.responseReceived = responseReceived;
            this.failure = failure;
        }

        @Override
        protected void initChannel(Channel channel) {
            ChannelPipeline pipeline = channel.pipeline();
            pipeline.addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                    response.set(msg.toString(CharsetUtil.UTF_8));
                    responseReceived.countDown();
                    ctx.close();
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    failure.compareAndSet(null, cause);
                    responseReceived.countDown();
                    ctx.close();
                }
            });
        }
    }
}
