/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_classes_io_uring;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringAdaptiveBufferRingAllocator;
import io.netty.channel.uring.IoUringBufferRingAllocator;
import io.netty.channel.uring.IoUringBufferRingConfig;
import io.netty.channel.uring.IoUringChannelOption;
import io.netty.channel.uring.IoUringDatagramChannel;
import io.netty.channel.uring.IoUringFixedBufferRingAllocator;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringIoHandlerConfig;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import io.netty.channel.uring.IoUringTcpInfo;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

public class Netty_transport_classes_io_uringTest {
    @Test
    void ioUringAvailabilityContractIsSelfConsistent() {
        boolean available = IoUring.isAvailable();
        Throwable cause = IoUring.unavailabilityCause();

        assertThat(available).isEqualTo(cause == null);
        if (available) {
            assertThatCode(IoUring::ensureAvailability).doesNotThrowAnyException();
            return;
        }

        assertThat(cause).isNotNull();
        assertThat(IoUring.isTcpFastOpenClientSideAvailable()).isFalse();
        assertThat(IoUring.isTcpFastOpenServerSideAvailable()).isFalse();
        assertThat(IoUring.isSpliceSupported()).isFalse();
        assertThat(IoUring.isRegisterBufferRingSupported()).isFalse();
        assertThat(IoUring.isRegisterBufferRingIncSupported()).isFalse();
        assertThat(IoUring.isAcceptMultishotEnabled()).isFalse();
        assertThat(IoUring.isRecvMultishotEnabled()).isFalse();
        assertThat(IoUring.isRecvsendBundleEnabled()).isFalse();
        assertThat(IoUring.isPollAddMultishotEnabled()).isFalse();
        assertThat(IoUringDatagramChannel.isSegmentedDatagramPacketSupported()).isFalse();

        Throwable thrown = catchThrowable(IoUring::ensureAvailability);
        assertThat(thrown).isInstanceOf(UnsatisfiedLinkError.class);
        assertThat(thrown).hasMessageContaining("required native library");
        assertThat(thrown.getCause()).isSameAs(cause);
        assertThat(rootCauseMessage(thrown)).isNotBlank();
    }

    @Test
    void publicConstructorsRespectTransportAvailability() {
        assertChannelConstructorMatchesAvailability(IoUringSocketChannel::new);
        assertChannelConstructorMatchesAvailability(IoUringServerSocketChannel::new);
        assertChannelConstructorMatchesAvailability(IoUringDatagramChannel::new);
        assertChannelConstructorMatchesAvailability(() -> new IoUringDatagramChannel(SocketProtocolFamily.INET));

        if (IoUring.isAvailable()) {
            IoHandlerFactory factory = IoUringIoHandler.newFactory(new IoUringIoHandlerConfig().setRingSize(8));
            EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, factory);
            try {
                assertThat(group.isShuttingDown()).isFalse();
            } finally {
                group.shutdownGracefully().syncUninterruptibly();
            }
            return;
        }

        assertThatThrownBy(IoUringIoHandler::newFactory).isInstanceOf(LinkageError.class);
        assertThatThrownBy(() -> IoUringIoHandler.newFactory(new IoUringIoHandlerConfig()))
                .isInstanceOf(LinkageError.class);
        assertThatThrownBy(() -> new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory(8)))
                .isInstanceOf(LinkageError.class);
    }

    @Test
    void channelOptionsAreRegisteredInTheConstantPool() {
        List<ChannelOption<?>> options = List.of(
                IoUringChannelOption.TCP_CORK,
                IoUringChannelOption.TCP_NOTSENT_LOWAT,
                IoUringChannelOption.TCP_KEEPIDLE,
                IoUringChannelOption.TCP_KEEPINTVL,
                IoUringChannelOption.TCP_KEEPCNT,
                IoUringChannelOption.TCP_USER_TIMEOUT,
                IoUringChannelOption.IP_FREEBIND,
                IoUringChannelOption.IP_TRANSPARENT,
                IoUringChannelOption.TCP_FASTOPEN,
                IoUringChannelOption.TCP_DEFER_ACCEPT,
                IoUringChannelOption.TCP_QUICKACK,
                IoUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE,
                IoUringChannelOption.IO_URING_BUFFER_GROUP_ID
        );

        assertThat(options).doesNotContainNull();
        assertThat(new LinkedHashSet<>(options)).hasSize(options.size());
        for (ChannelOption<?> option : options) {
            assertThat(option.name()).isNotBlank();
            assertThat(ChannelOption.valueOf(option.name())).isSameAs(option);
        }
    }

    @Test
    void bufferRingAllocatorsCreateDirectBuffers() {
        IoUringFixedBufferRingAllocator fixedAllocator = new IoUringFixedBufferRingAllocator(
                UnpooledByteBufAllocator.DEFAULT,
                128
        );
        ByteBuf fixed = fixedAllocator.allocate();
        try {
            assertThat(fixed.isDirect()).isTrue();
            assertThat(fixed.capacity()).isEqualTo(128);
            fixedAllocator.lastBytesRead(128, 128);
        } finally {
            fixed.release();
        }

        IoUringAdaptiveBufferRingAllocator adaptiveAllocator = new IoUringAdaptiveBufferRingAllocator(
                UnpooledByteBufAllocator.DEFAULT,
                64,
                128,
                256
        );
        ByteBuf adaptive = adaptiveAllocator.allocate();
        try {
            assertThat(adaptive.isDirect()).isTrue();
            assertThat(adaptive.capacity()).isBetween(64, 256);
            adaptiveAllocator.lastBytesRead(adaptive.capacity(), adaptive.capacity());
        } finally {
            adaptive.release();
        }
    }

    @Test
    void bufferRingConfigurationExposesValueSemanticsAndValidation() {
        IoUringBufferRingAllocator allocator = new IoUringFixedBufferRingAllocator(
                UnpooledByteBufAllocator.DEFAULT,
                64
        );
        IoUringBufferRingConfig config = new IoUringBufferRingConfig(
                (short) 7, (short) 8, 4, 16, false, allocator
        );
        IoUringBufferRingConfig sameGroup = new IoUringBufferRingConfig(
                (short) 7, (short) 16, 8, 16, false, allocator
        );
        IoUringBufferRingConfig differentGroup = new IoUringBufferRingConfig(
                (short) 8, (short) 8, 4, 16, false, allocator
        );

        assertThat(config.bufferGroupId()).isEqualTo((short) 7);
        assertThat(config.bufferRingSize()).isEqualTo((short) 8);
        assertThat(config.batchSize()).isEqualTo(4);
        assertThat(config.maxUnreleasedBuffers()).isEqualTo(16);
        assertThat(config.allocator()).isSameAs(allocator);
        assertThat(config.isIncremental()).isFalse();
        assertThat(config).isEqualTo(sameGroup).hasSameHashCodeAs(sameGroup);
        assertThat(config).isNotEqualTo(differentGroup).isNotEqualTo("7");

        IoUringBufferRingConfig shortConstructor = new IoUringBufferRingConfig(
                (short) 9, (short) 8, 32, allocator
        );
        assertThat(shortConstructor.batchSize()).isEqualTo(4);
        assertThat(shortConstructor.maxUnreleasedBuffers()).isEqualTo(32);
        assertThat(shortConstructor.isIncremental()).isEqualTo(IoUring.isRegisterBufferRingIncSupported());

        assertThatThrownBy(() -> new IoUringBufferRingConfig((short) -1, (short) 8, 4, 16, false, allocator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bgId");
        assertThatThrownBy(() -> new IoUringBufferRingConfig((short) 1, (short) 0, 4, 16, false, allocator))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IoUringBufferRingConfig((short) 1, (short) 6, 4, 16, false, allocator))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IoUringBufferRingConfig((short) 1, (short) 8, 0, 16, false, allocator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
        assertThatThrownBy(() -> new IoUringBufferRingConfig((short) 1, (short) 8, 4, 7, false, allocator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxUnreleasedBuffers");
        assertThatThrownBy(() -> new IoUringBufferRingConfig((short) 1, (short) 8, 4, 16, false, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("allocator");
    }

    @Test
    void ioHandlerConfigurationRoundTripsValuesAndRejectsInvalidInput() {
        if (!IoUring.isAvailable()) {
            Throwable thrown = catchThrowable(IoUringIoHandlerConfig::new);
            if (thrown != null) {
                assertThat(thrown).isInstanceOf(LinkageError.class);
                return;
            }
        }

        IoUringBufferRingAllocator allocator = new IoUringFixedBufferRingAllocator(64);
        IoUringBufferRingConfig firstRing = new IoUringBufferRingConfig(
                (short) 1, (short) 8, 4, 16, false, allocator
        );
        IoUringBufferRingConfig secondRing = new IoUringBufferRingConfig(
                (short) 2, (short) 16, 8, 32, false, allocator
        );
        IoUringIoHandlerConfig config = new IoUringIoHandlerConfig()
                .setRingSize(8)
                .setCqSize(16)
                .setMaxBoundedWorker(2)
                .setMaxUnboundedWorker(3)
                .setBufferRingConfig(firstRing, secondRing);

        assertThat(config.getRingSize()).isEqualTo(8);
        assertThat(config.getCqSize()).isEqualTo(16);
        assertThat(config.getMaxBoundedWorker()).isEqualTo(2);
        assertThat(config.getMaxUnboundedWorker()).isEqualTo(3);
        assertThat(config.getBufferRingConfigs()).containsExactlyInAnyOrder(firstRing, secondRing);
        List<IoUringBufferRingConfig> copy = config.getBufferRingConfigs();
        copy.clear();
        assertThat(config.getBufferRingConfigs()).containsExactlyInAnyOrder(firstRing, secondRing);

        IoUringBufferRingConfig duplicateGroup = new IoUringBufferRingConfig(
                (short) 1, (short) 32, 8, 32, false, allocator
        );
        assertThatThrownBy(() -> config.setBufferRingConfig(firstRing, duplicateGroup))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bufferGroupId 1");
        assertThatThrownBy(() -> new IoUringIoHandlerConfig().setRingSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ringSize");
        assertThatThrownBy(() -> new IoUringIoHandlerConfig().setCqSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cqSize");
        assertThatThrownBy(() -> new IoUringIoHandlerConfig().setRingSize(8).setCqSize(4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cqSize must be greater than or equal to ringSize");
        assertThatThrownBy(() -> new IoUringIoHandlerConfig().setRingSize(8).setCqSize(12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IoUringIoHandlerConfig().setMaxBoundedWorker(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBoundedWorker");
        assertThatThrownBy(() -> new IoUringIoHandlerConfig().setMaxUnboundedWorker(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxUnboundedWorker");
    }

    @Test
    void defaultTcpInfoContainsKernelCounterSnapshotAccessors() {
        IoUringTcpInfo tcpInfo = new IoUringTcpInfo();
        assertThat(new long[] {
                tcpInfo.state(), tcpInfo.caState(), tcpInfo.retransmits(), tcpInfo.probes(), tcpInfo.backoff(),
                tcpInfo.options(), tcpInfo.sndWscale(), tcpInfo.rcvWscale(), tcpInfo.rto(), tcpInfo.ato(),
                tcpInfo.sndMss(), tcpInfo.rcvMss(), tcpInfo.unacked(), tcpInfo.sacked(), tcpInfo.lost(),
                tcpInfo.retrans(), tcpInfo.fackets(), tcpInfo.lastDataSent(), tcpInfo.lastAckSent(),
                tcpInfo.lastDataRecv(), tcpInfo.lastAckRecv(), tcpInfo.pmtu(), tcpInfo.rcvSsthresh(), tcpInfo.rtt(),
                tcpInfo.rttvar(), tcpInfo.sndSsthresh(), tcpInfo.sndCwnd(), tcpInfo.advmss(), tcpInfo.reordering(),
                tcpInfo.rcvRtt(), tcpInfo.rcvSpace(), tcpInfo.totalRetrans()
        }).containsOnly(0L);
    }

    @Test
    void tcpChannelsCanExchangeBytesWhenAvailable() throws Exception {
        if (!IoUring.isAvailable()) {
            assertThatThrownBy(IoUringIoHandler::newFactory).isInstanceOf(LinkageError.class);
            return;
        }

        EventLoopGroup bossGroup = null;
        EventLoopGroup workerGroup = null;
        EventLoopGroup clientGroup = null;
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            bossGroup = newIoUringEventLoopGroup();
            workerGroup = newIoUringEventLoopGroup();
            clientGroup = newIoUringEventLoopGroup();
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<String> response = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            serverChannel = awaitSuccess(new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(IoUringServerSocketChannel.class)
                    .childHandler(new EchoServerInitializer(failure))
                    .bind(new InetSocketAddress("127.0.0.1", 0))).channel();
            int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
            clientChannel = awaitSuccess(new Bootstrap()
                    .group(clientGroup)
                    .channel(IoUringSocketChannel.class)
                    .handler(new EchoClientInitializer(response, received, failure))
                    .connect("127.0.0.1", port)).channel();
            awaitSuccess(clientChannel.writeAndFlush(Unpooled.copiedBuffer("ping", CharsetUtil.UTF_8)));

            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(response.get()).isEqualTo("echo:ping");
        } finally {
            close(serverChannel);
            close(clientChannel);
            shutdown(clientGroup);
            shutdown(workerGroup);
            shutdown(bossGroup);
        }
    }

    @Test
    void tcpChannelsCanSendFileRegionsWhenAvailable(@TempDir Path tempDir) throws Exception {
        if (!IoUring.isAvailable()) {
            assertThatThrownBy(IoUringIoHandler::newFactory).isInstanceOf(LinkageError.class);
            return;
        }

        String payload = "file-region payload\ntransferred by io_uring";
        Path source = tempDir.resolve("payload.txt");
        Files.writeString(source, payload, StandardCharsets.UTF_8);
        long payloadSize = Files.size(source);
        EventLoopGroup bossGroup = null;
        EventLoopGroup workerGroup = null;
        EventLoopGroup clientGroup = null;
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            bossGroup = newIoUringEventLoopGroup();
            workerGroup = newIoUringEventLoopGroup();
            clientGroup = newIoUringEventLoopGroup();
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<String> receivedContent = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            serverChannel = awaitSuccess(new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(IoUringServerSocketChannel.class)
                    .childHandler(new FileRegionServerInitializer(payloadSize, receivedContent, received, failure))
                    .bind(new InetSocketAddress("127.0.0.1", 0))).channel();
            int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
            clientChannel = awaitSuccess(new Bootstrap()
                    .group(clientGroup)
                    .channel(IoUringSocketChannel.class)
                    .handler(new NoopInitializer())
                    .connect("127.0.0.1", port)).channel();

            awaitSuccess(clientChannel.writeAndFlush(new DefaultFileRegion(source.toFile(), 0, payloadSize)));

            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(receivedContent.get()).isEqualTo(payload);
        } finally {
            close(clientChannel);
            close(serverChannel);
            shutdown(clientGroup);
            shutdown(workerGroup);
            shutdown(bossGroup);
        }
    }

    @Test
    void datagramChannelsCanExchangePacketsWhenAvailable() throws Exception {
        if (!IoUring.isAvailable()) {
            assertThatThrownBy(IoUringIoHandler::newFactory).isInstanceOf(LinkageError.class);
            return;
        }

        EventLoopGroup serverGroup = null;
        EventLoopGroup clientGroup = null;
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            serverGroup = newIoUringEventLoopGroup();
            clientGroup = newIoUringEventLoopGroup();
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<String> response = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            serverChannel = awaitSuccess(new Bootstrap()
                    .group(serverGroup)
                    .channel(IoUringDatagramChannel.class)
                    .handler(new DatagramEchoServerInitializer(failure))
                    .bind(new InetSocketAddress("127.0.0.1", 0))).channel();
            clientChannel = awaitSuccess(new Bootstrap()
                    .group(clientGroup)
                    .channel(IoUringDatagramChannel.class)
                    .handler(new DatagramEchoClientInitializer(response, received, failure))
                    .bind(new InetSocketAddress("127.0.0.1", 0))).channel();
            awaitSuccess(clientChannel.writeAndFlush(new DatagramPacket(
                    Unpooled.copiedBuffer("ping", CharsetUtil.UTF_8),
                    (InetSocketAddress) serverChannel.localAddress()
            )));

            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(response.get()).isEqualTo("datagram:ping");
        } finally {
            close(serverChannel);
            close(clientChannel);
            shutdown(clientGroup);
            shutdown(serverGroup);
        }
    }

    @Test
    void connectedDatagramChannelsCanSendRawBuffersWhenAvailable() throws Exception {
        if (!IoUring.isAvailable()) {
            assertThatThrownBy(IoUringIoHandler::newFactory).isInstanceOf(LinkageError.class);
            return;
        }

        EventLoopGroup serverGroup = null;
        EventLoopGroup clientGroup = null;
        Channel serverChannel = null;
        IoUringDatagramChannel clientChannel = null;
        try {
            serverGroup = newIoUringEventLoopGroup();
            clientGroup = newIoUringEventLoopGroup();
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<String> response = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            serverChannel = awaitSuccess(new Bootstrap()
                    .group(serverGroup)
                    .channel(IoUringDatagramChannel.class)
                    .handler(new DatagramEchoServerInitializer(failure))
                    .bind(new InetSocketAddress("127.0.0.1", 0))).channel();
            clientChannel = (IoUringDatagramChannel) awaitSuccess(new Bootstrap()
                    .group(clientGroup)
                    .channel(IoUringDatagramChannel.class)
                    .handler(new ConnectedDatagramEchoClientInitializer(response, received, failure))
                    .connect(serverChannel.localAddress())).channel();

            assertThat(clientChannel.isConnected()).isTrue();
            assertThat(clientChannel.remoteAddress()).isEqualTo(serverChannel.localAddress());

            awaitSuccess(clientChannel.writeAndFlush(Unpooled.copiedBuffer("connected-ping", CharsetUtil.UTF_8)));

            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(response.get()).isEqualTo("datagram:connected-ping");

            awaitSuccess(clientChannel.disconnect());
            assertThat(clientChannel.isConnected()).isFalse();
        } finally {
            close(serverChannel);
            close(clientChannel);
            shutdown(clientGroup);
            shutdown(serverGroup);
        }
    }

    private static void assertChannelConstructorMatchesAvailability(Supplier<? extends Channel> constructor) {
        if (IoUring.isAvailable()) {
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

    private static EventLoopGroup newIoUringEventLoopGroup() {
        IoUringIoHandlerConfig config = new IoUringIoHandlerConfig().setRingSize(8);
        return new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory(config));
    }

    private static ChannelFuture awaitSuccess(ChannelFuture future) throws InterruptedException {
        assertThat(future.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(future.cause()).isNull();
        assertThat(future.isSuccess()).isTrue();
        return future;
    }

    private static void close(Channel channel) {
        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
    }

    private static void shutdown(EventLoopGroup group) {
        if (group != null) {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getName() : current.getMessage();
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
                    String response = "echo:" + msg.toString(CharsetUtil.UTF_8);
                    ctx.writeAndFlush(Unpooled.copiedBuffer(response, CharsetUtil.UTF_8))
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
        private final CountDownLatch received;
        private final AtomicReference<Throwable> failure;

        private EchoClientInitializer(
                AtomicReference<String> response,
                CountDownLatch received,
                AtomicReference<Throwable> failure
        ) {
            this.response = response;
            this.received = received;
            this.failure = failure;
        }

        @Override
        protected void initChannel(Channel channel) {
            channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                    response.set(msg.toString(CharsetUtil.UTF_8));
                    received.countDown();
                    ctx.close();
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    failure.compareAndSet(null, cause);
                    received.countDown();
                    ctx.close();
                }
            });
        }
    }

    private static final class FileRegionServerInitializer extends ChannelInitializer<Channel> {
        private final long expectedBytes;
        private final AtomicReference<String> receivedContent;
        private final CountDownLatch received;
        private final AtomicReference<Throwable> failure;

        private FileRegionServerInitializer(
                long expectedBytes,
                AtomicReference<String> receivedContent,
                CountDownLatch received,
                AtomicReference<Throwable> failure
        ) {
            this.expectedBytes = expectedBytes;
            this.receivedContent = receivedContent;
            this.received = received;
            this.failure = failure;
        }

        @Override
        protected void initChannel(Channel channel) {
            channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                private final StringBuilder content = new StringBuilder();
                private long receivedBytes;

                @Override
                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                    receivedBytes += msg.readableBytes();
                    content.append(msg.toString(CharsetUtil.UTF_8));
                    if (receivedBytes >= expectedBytes) {
                        receivedContent.set(content.toString());
                        received.countDown();
                        ctx.close();
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    failure.compareAndSet(null, cause);
                    received.countDown();
                    ctx.close();
                }
            });
        }
    }

    private static final class NoopInitializer extends ChannelInitializer<Channel> {
        @Override
        protected void initChannel(Channel channel) {
            channel.config().setAutoRead(true);
        }
    }

    private static final class DatagramEchoServerInitializer extends ChannelInitializer<Channel> {
        private final AtomicReference<Throwable> failure;

        private DatagramEchoServerInitializer(AtomicReference<Throwable> failure) {
            this.failure = failure;
        }

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
    }

    private static final class DatagramEchoClientInitializer extends ChannelInitializer<Channel> {
        private final AtomicReference<String> response;
        private final CountDownLatch received;
        private final AtomicReference<Throwable> failure;

        private DatagramEchoClientInitializer(
                AtomicReference<String> response,
                CountDownLatch received,
                AtomicReference<Throwable> failure
        ) {
            this.response = response;
            this.received = received;
            this.failure = failure;
        }

        @Override
        protected void initChannel(Channel channel) {
            channel.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                    response.set(packet.content().toString(CharsetUtil.UTF_8));
                    received.countDown();
                    ctx.close();
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    failure.compareAndSet(null, cause);
                    received.countDown();
                    ctx.close();
                }
            });
        }
    }

    private static final class ConnectedDatagramEchoClientInitializer extends ChannelInitializer<Channel> {
        private final AtomicReference<String> response;
        private final CountDownLatch received;
        private final AtomicReference<Throwable> failure;

        private ConnectedDatagramEchoClientInitializer(
                AtomicReference<String> response,
                CountDownLatch received,
                AtomicReference<Throwable> failure
        ) {
            this.response = response;
            this.received = received;
            this.failure = failure;
        }

        @Override
        protected void initChannel(Channel channel) {
            channel.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                    response.set(packet.content().toString(CharsetUtil.UTF_8));
                    received.countDown();
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    failure.compareAndSet(null, cause);
                    received.countDown();
                    ctx.close();
                }
            });
        }
    }
}
