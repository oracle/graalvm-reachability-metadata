/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty_incubator.netty_incubator_transport_classes_io_uring;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.incubator.channel.uring.IOUring;
import io.netty.incubator.channel.uring.IOUringChannelOption;
import io.netty.incubator.channel.uring.IOUringDatagramChannel;
import io.netty.incubator.channel.uring.IOUringDatagramChannelConfig;
import io.netty.incubator.channel.uring.IOUringEventLoopGroup;
import io.netty.incubator.channel.uring.IOUringServerSocketChannel;
import io.netty.incubator.channel.uring.IOUringServerSocketChannelConfig;
import io.netty.incubator.channel.uring.IOUringSocketChannel;
import io.netty.incubator.channel.uring.IOUringSocketChannelConfig;
import io.netty.incubator.channel.uring.IOUringTcpInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

public class Netty_incubator_transport_classes_io_uringTest {
    private static final long FUTURE_TIMEOUT_SECONDS = 5;

    @Test
    void ioUringAvailabilityContractIsSelfConsistent() {
        boolean available = IOUring.isAvailable();
        Throwable cause = IOUring.unavailabilityCause();

        assertThat(available).isEqualTo(cause == null);
        if (available) {
            assertThatCode(IOUring::ensureAvailability).doesNotThrowAnyException();
            assertThatCode(IOUring::isTcpFastOpenClientSideAvailable).doesNotThrowAnyException();
            assertThatCode(IOUring::isTcpFastOpenServerSideAvailable).doesNotThrowAnyException();
            assertThat(IOUringDatagramChannel.isSegmentedDatagramPacketSupported()).isTrue();
            return;
        }

        assertThat(cause).isNotNull();
        assertThat(IOUring.isTcpFastOpenClientSideAvailable()).isFalse();
        assertThat(IOUring.isTcpFastOpenServerSideAvailable()).isFalse();
        assertThat(IOUringDatagramChannel.isSegmentedDatagramPacketSupported()).isFalse();

        Throwable thrown = catchThrowable(IOUring::ensureAvailability);
        assertThat(thrown).isInstanceOf(UnsatisfiedLinkError.class);
        assertThat(thrown).hasMessageContaining("required native library");
        assertThat(thrown.getCause()).isSameAs(cause);
        assertThat(rootCauseMessage(thrown)).isNotBlank();
    }

    @Test
    void publicChannelConstructorsRespectTransportAvailability() {
        assertChannelConstructorMatchesAvailability(IOUringSocketChannel::new);
        assertChannelConstructorMatchesAvailability(IOUringServerSocketChannel::new);
        assertChannelConstructorMatchesAvailability(IOUringDatagramChannel::new);
    }

    @Test
    void datagramProtocolFamilyConstructorsRespectTransportAvailability() {
        assertChannelConstructorMatchesAvailability(() -> new IOUringDatagramChannel(InternetProtocolFamily.IPv4));
    }

    @Test
    void eventLoopGroupConstructorRespectsTransportAvailability() {
        AtomicInteger threadCounter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "iouring-test-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };

        assertEventLoopGroupConstructorMatchesAvailability(() -> new IOUringEventLoopGroup(1, factory, 8, 2));
    }

    @Test
    void ioUringChannelOptionsAreRegisteredInTheConstantPool() {
        List<ChannelOption<?>> options = List.of(
                IOUringChannelOption.TCP_CORK,
                IOUringChannelOption.TCP_NOTSENT_LOWAT,
                IOUringChannelOption.TCP_KEEPIDLE,
                IOUringChannelOption.TCP_KEEPINTVL,
                IOUringChannelOption.TCP_KEEPCNT,
                IOUringChannelOption.TCP_USER_TIMEOUT,
                IOUringChannelOption.IP_FREEBIND,
                IOUringChannelOption.IP_TRANSPARENT,
                IOUringChannelOption.IP_RECVORIGDSTADDR,
                IOUringChannelOption.TCP_DEFER_ACCEPT,
                IOUringChannelOption.TCP_QUICKACK,
                IOUringChannelOption.TCP_MD5SIG,
                IOUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE
        );

        assertThat(options).doesNotContainNull();
        assertThat(new LinkedHashSet<>(options)).hasSize(options.size());

        for (ChannelOption<?> option : options) {
            assertThat(option.name()).isNotBlank();
            assertThat(ChannelOption.valueOf(option.name())).isSameAs(option);
        }
    }

    @Test
    void ioUringFastOpenOptionsReuseTheSharedChannelOptionInstances() {
        assertThat(IOUringChannelOption.TCP_FASTOPEN).isSameAs(ChannelOption.TCP_FASTOPEN);
        assertThat(IOUringChannelOption.TCP_FASTOPEN.name())
                .isEqualTo(ChannelOption.TCP_FASTOPEN.name())
                .contains("TCP_FASTOPEN");

        assertThat(IOUringChannelOption.TCP_FASTOPEN_CONNECT).isSameAs(ChannelOption.TCP_FASTOPEN_CONNECT);
        assertThat(IOUringChannelOption.TCP_FASTOPEN_CONNECT.name())
                .isEqualTo(ChannelOption.TCP_FASTOPEN_CONNECT.name())
                .contains("TCP_FASTOPEN_CONNECT");

        assertThat(ChannelOption.valueOf(IOUringChannelOption.TCP_FASTOPEN.name()))
                .isSameAs(IOUringChannelOption.TCP_FASTOPEN);
        assertThat(ChannelOption.valueOf(IOUringChannelOption.TCP_FASTOPEN_CONNECT.name()))
                .isSameAs(IOUringChannelOption.TCP_FASTOPEN_CONNECT);
    }

    @Test
    void tcpInfoStartsWithZeroedKernelCounters() {
        IOUringTcpInfo tcpInfo = new IOUringTcpInfo();

        assertThat(new long[] {
                tcpInfo.state(),
                tcpInfo.caState(),
                tcpInfo.retransmits(),
                tcpInfo.probes(),
                tcpInfo.backoff(),
                tcpInfo.options(),
                tcpInfo.sndWscale(),
                tcpInfo.rcvWscale(),
                tcpInfo.rto(),
                tcpInfo.ato(),
                tcpInfo.sndMss(),
                tcpInfo.rcvMss(),
                tcpInfo.unacked(),
                tcpInfo.sacked(),
                tcpInfo.lost(),
                tcpInfo.retrans(),
                tcpInfo.fackets(),
                tcpInfo.lastDataSent(),
                tcpInfo.lastAckSent(),
                tcpInfo.lastDataRecv(),
                tcpInfo.lastAckRecv(),
                tcpInfo.pmtu(),
                tcpInfo.rcvSsthresh(),
                tcpInfo.rtt(),
                tcpInfo.rttvar(),
                tcpInfo.sndSsthresh(),
                tcpInfo.sndCwnd(),
                tcpInfo.advmss(),
                tcpInfo.reordering(),
                tcpInfo.rcvRtt(),
                tcpInfo.rcvSpace(),
                tcpInfo.totalRetrans()
        }).containsOnly(0L);
    }

    @Test
    void socketChannelConfigRoundTripsPublicTransportOptionsWhenAvailable() {
        if (!IOUring.isAvailable()) {
            assertThatThrownBy(IOUringSocketChannel::new).isInstanceOf(UnsatisfiedLinkError.class);
            return;
        }

        IOUringSocketChannel channel = new IOUringSocketChannel();
        try {
            IOUringSocketChannelConfig config = channel.config();

            assertThat(config.getOptions()).containsKeys(
                    IOUringChannelOption.TCP_CORK,
                    IOUringChannelOption.TCP_NOTSENT_LOWAT,
                    IOUringChannelOption.TCP_KEEPIDLE,
                    IOUringChannelOption.TCP_KEEPINTVL,
                    IOUringChannelOption.TCP_KEEPCNT,
                    IOUringChannelOption.TCP_USER_TIMEOUT,
                    IOUringChannelOption.TCP_QUICKACK,
                    IOUringChannelOption.TCP_FASTOPEN_CONNECT
            );

            assertThat(config.setAllowHalfClosure(true)).isSameAs(config);
            assertThat(config.isAllowHalfClosure()).isTrue();
            assertThat(config.setOption(IOUringChannelOption.TCP_CORK, true)).isTrue();
            assertThat(config.getOption(IOUringChannelOption.TCP_CORK)).isTrue();
            assertThat(config.setOption(IOUringChannelOption.TCP_CORK, false)).isTrue();
            assertThat(config.isTcpCork()).isFalse();
            assertThat(config.setOption(IOUringChannelOption.TCP_FASTOPEN_CONNECT, false)).isTrue();
            assertThat(config.isTcpFastOpenConnect()).isFalse();
            assertThat(config.setOption(IOUringChannelOption.TCP_KEEPIDLE, 30)).isTrue();
            assertThat(config.getTcpKeepIdle()).isEqualTo(30);
            assertThat(config.setOption(IOUringChannelOption.TCP_KEEPINTVL, 10)).isTrue();
            assertThat(config.getTcpKeepIntvl()).isEqualTo(10);
            assertThat(config.setOption(IOUringChannelOption.TCP_KEEPCNT, 5)).isTrue();
            assertThat(config.getTcpKeepCnt()).isEqualTo(5);
            assertThat(config.setOption(IOUringChannelOption.TCP_USER_TIMEOUT, 1_000)).isTrue();
            assertThat(config.getTcpUserTimeout()).isEqualTo(1_000);
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    void serverSocketChannelConfigRoundTripsPublicTransportOptionsWhenAvailable() {
        if (!IOUring.isAvailable()) {
            assertThatThrownBy(IOUringServerSocketChannel::new).isInstanceOf(UnsatisfiedLinkError.class);
            return;
        }

        IOUringServerSocketChannel channel = new IOUringServerSocketChannel();
        try {
            IOUringServerSocketChannelConfig config = channel.config();

            assertThat(config.getOptions()).containsKeys(
                    IOUringChannelOption.TCP_DEFER_ACCEPT,
                    IOUringChannelOption.TCP_FASTOPEN,
                    IOUringChannelOption.IP_FREEBIND,
                    IOUringChannelOption.IP_TRANSPARENT
            );

            assertThat(config.setBacklog(16)).isSameAs(config);
            assertThat(config.getBacklog()).isEqualTo(16);
            assertThat(config.setOption(IOUringChannelOption.TCP_DEFER_ACCEPT, 2)).isTrue();
            assertThat(config.getTcpDeferAccept()).isEqualTo(2);
            assertThat(config.setOption(IOUringChannelOption.TCP_FASTOPEN, 4)).isTrue();
            assertThat(config.getTcpFastopen()).isEqualTo(4);
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    void datagramChannelConfigRoundTripsPublicTransportOptionsWhenAvailable() {
        if (!IOUring.isAvailable()) {
            assertThatThrownBy(IOUringDatagramChannel::new).isInstanceOf(UnsatisfiedLinkError.class);
            return;
        }

        IOUringDatagramChannel channel = new IOUringDatagramChannel();
        try {
            IOUringDatagramChannelConfig config = channel.config();

            assertThat(config.getOptions()).containsKeys(
                    IOUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE,
                    IOUringChannelOption.IP_FREEBIND,
                    IOUringChannelOption.IP_TRANSPARENT
            );

            assertThat(config.setOption(IOUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, 1_200)).isTrue();
            assertThat(config.getMaxDatagramPayloadSize()).isEqualTo(1_200);
            assertThat(config.setMaxDatagramPayloadSize(512)).isSameAs(config);
            assertThat(config.getOption(IOUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE)).isEqualTo(512);
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    @Test
    void socketChannelsExchangeDataThroughBootstrapWhenAvailable() throws InterruptedException {
        if (!IOUring.isAvailable()) {
            assertThat(IOUring.unavailabilityCause()).isNotNull();
            return;
        }

        EventLoopGroup bossGroup = new IOUringEventLoopGroup(1);
        EventLoopGroup workerGroup = new IOUringEventLoopGroup(1);
        EventLoopGroup clientGroup = new IOUringEventLoopGroup(1);
        Channel serverChannel = null;
        Channel clientChannel = null;
        CountDownLatch responseReceived = new CountDownLatch(1);
        AtomicReference<String> clientResponse = new AtomicReference<>();
        AtomicReference<Throwable> serverError = new AtomicReference<>();
        AtomicReference<Throwable> clientError = new AtomicReference<>();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channelFactory((ChannelFactory<IOUringServerSocketChannel>) IOUringServerSocketChannel::new)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext context, Object message) {
                                    context.writeAndFlush(message);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
                                    serverError.set(cause);
                                    context.close();
                                }
                            });
                        }
                    });
            ChannelFuture bindFuture = serverBootstrap.bind(new InetSocketAddress("127.0.0.1", 0));
            assertFutureCompletesSuccessfully(bindFuture);
            serverChannel = bindFuture.channel();

            Bootstrap clientBootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channelFactory((ChannelFactory<IOUringSocketChannel>) IOUringSocketChannel::new)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
                                    clientResponse.set(message.toString(StandardCharsets.UTF_8));
                                    responseReceived.countDown();
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
                                    clientError.set(cause);
                                    responseReceived.countDown();
                                    context.close();
                                }
                            });
                        }
                    });
            ChannelFuture connectFuture = clientBootstrap.connect(serverChannel.localAddress());
            assertFutureCompletesSuccessfully(connectFuture);
            clientChannel = connectFuture.channel();

            String payloadText = "io_uring bootstrap round trip";
            ChannelFuture writeFuture = clientChannel.writeAndFlush(
                    Unpooled.copiedBuffer(payloadText, StandardCharsets.UTF_8));
            assertFutureCompletesSuccessfully(writeFuture);

            assertThat(responseReceived.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(serverError.get()).isNull();
            assertThat(clientError.get()).isNull();
            assertThat(clientResponse.get()).isEqualTo(payloadText);
        } finally {
            closeChannel(clientChannel);
            closeChannel(serverChannel);
            shutdownGroup(clientGroup);
            shutdownGroup(workerGroup);
            shutdownGroup(bossGroup);
        }
    }

    @Test
    void datagramChannelsExchangePacketsThroughBootstrapWhenAvailable() throws InterruptedException {
        if (!IOUring.isAvailable()) {
            assertThat(IOUring.unavailabilityCause()).isNotNull();
            return;
        }

        EventLoopGroup receiverGroup = new IOUringEventLoopGroup(1);
        EventLoopGroup senderGroup = new IOUringEventLoopGroup(1);
        Channel receiverChannel = null;
        Channel senderChannel = null;
        CountDownLatch packetReceived = new CountDownLatch(1);
        AtomicReference<String> receivedPayload = new AtomicReference<>();
        AtomicReference<InetSocketAddress> packetSender = new AtomicReference<>();
        AtomicReference<Throwable> receiverError = new AtomicReference<>();
        AtomicReference<Throwable> senderError = new AtomicReference<>();

        try {
            Bootstrap receiverBootstrap = new Bootstrap()
                    .group(receiverGroup)
                    .channelFactory((ChannelFactory<IOUringDatagramChannel>) IOUringDatagramChannel::new)
                    .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext context, DatagramPacket packet) {
                            receivedPayload.set(packet.content().toString(StandardCharsets.UTF_8));
                            packetSender.set(packet.sender());
                            packetReceived.countDown();
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
                            receiverError.set(cause);
                            packetReceived.countDown();
                            context.close();
                        }
                    });
            ChannelFuture receiverBindFuture = receiverBootstrap.bind(new InetSocketAddress("127.0.0.1", 0));
            assertFutureCompletesSuccessfully(receiverBindFuture);
            receiverChannel = receiverBindFuture.channel();

            Bootstrap senderBootstrap = new Bootstrap()
                    .group(senderGroup)
                    .channelFactory((ChannelFactory<IOUringDatagramChannel>) IOUringDatagramChannel::new)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
                            senderError.set(cause);
                            context.close();
                        }
                    });
            ChannelFuture senderBindFuture = senderBootstrap.bind(new InetSocketAddress("127.0.0.1", 0));
            assertFutureCompletesSuccessfully(senderBindFuture);
            senderChannel = senderBindFuture.channel();

            String payloadText = "io_uring datagram payload";
            ChannelFuture writeFuture = senderChannel.writeAndFlush(new DatagramPacket(
                    Unpooled.copiedBuffer(payloadText, StandardCharsets.UTF_8),
                    (InetSocketAddress) receiverChannel.localAddress()));
            assertFutureCompletesSuccessfully(writeFuture);

            assertThat(packetReceived.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(receiverError.get()).isNull();
            assertThat(senderError.get()).isNull();
            assertThat(receivedPayload.get()).isEqualTo(payloadText);
            assertThat(packetSender.get()).isNotNull();
            assertThat(packetSender.get().getPort())
                    .isEqualTo(((InetSocketAddress) senderChannel.localAddress()).getPort());
        } finally {
            closeChannel(senderChannel);
            closeChannel(receiverChannel);
            shutdownGroup(senderGroup);
            shutdownGroup(receiverGroup);
        }
    }

    private static void assertChannelConstructorMatchesAvailability(Supplier<? extends Channel> constructor) {
        if (IOUring.isAvailable()) {
            Channel channel = constructor.get();
            try {
                assertThat(channel.isOpen()).isTrue();
                assertThat(channel.isActive()).isFalse();
                assertThat(channel.config()).isNotNull();
                assertThat(channel.metadata()).isNotNull();
            } finally {
                channel.close().syncUninterruptibly();
            }
            return;
        }

        assertThatThrownBy(constructor::get).isInstanceOf(UnsatisfiedLinkError.class);
    }

    private static void assertEventLoopGroupConstructorMatchesAvailability(
            Supplier<? extends EventLoopGroup> constructor) {
        if (IOUring.isAvailable()) {
            EventLoopGroup group = constructor.get();
            try {
                assertThat(group.isShuttingDown()).isFalse();
                assertThat(group.next()).isNotNull();
            } finally {
                group.shutdownGracefully().syncUninterruptibly();
            }
            return;
        }

        assertThatThrownBy(constructor::get).isInstanceOf(UnsatisfiedLinkError.class);
    }

    private static void assertFutureCompletesSuccessfully(ChannelFuture future) throws InterruptedException {
        assertThat(future.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(future.cause()).isNull();
        assertThat(future.isSuccess()).isTrue();
    }

    private static void closeChannel(Channel channel) {
        if (channel != null) {
            channel.close().awaitUninterruptibly(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static void shutdownGroup(EventLoopGroup group) {
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS)
                .awaitUninterruptibly(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getName() : current.getMessage();
    }
}
