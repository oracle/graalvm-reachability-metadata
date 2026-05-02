/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_native_kqueue;

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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.kqueue.AcceptFilter;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueChannelOption;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannelConfig;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.DomainSocketReadMode;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

public class Netty_transport_native_kqueueTest {
    @Test
    void kqueueAvailabilityContractIsSelfConsistent() {
        boolean available = KQueue.isAvailable();
        Throwable cause = KQueue.unavailabilityCause();

        assertThat(available).isEqualTo(cause == null);
        if (available) {
            assertThatCode(KQueue::ensureAvailability).doesNotThrowAnyException();
            return;
        }

        assertThat(cause).isNotNull();
        Throwable thrown = catchThrowable(KQueue::ensureAvailability);
        assertThat(thrown).isInstanceOf(UnsatisfiedLinkError.class);
        assertThat(thrown.getCause()).isSameAs(cause);
    }

    @Test
    void kqueuePublicTypesRespectTransportAvailability() {
        assertChannelConstructorMatchesAvailability(KQueueSocketChannel::new);
        assertChannelConstructorMatchesAvailability(KQueueServerSocketChannel::new);
        assertChannelConstructorMatchesAvailability(KQueueDatagramChannel::new);
        assertChannelConstructorMatchesAvailability(KQueueDomainSocketChannel::new);
        assertChannelConstructorMatchesAvailability(KQueueServerDomainSocketChannel::new);
        assertEventLoopGroupConstructorMatchesAvailability(KQueueEventLoopGroup::new);
    }

    @Test
    void kqueueChannelOptionsAreRegisteredInTheConstantPool() {
        List<ChannelOption<?>> options = List.of(
                KQueueChannelOption.SO_SNDLOWAT,
                KQueueChannelOption.TCP_NOPUSH,
                KQueueChannelOption.SO_ACCEPTFILTER,
                KQueueChannelOption.RCV_ALLOC_TRANSPORT_PROVIDES_GUESS
        );

        assertThat(options).doesNotContainNull();
        assertThat(new LinkedHashSet<>(options)).hasSize(options.size());

        for (ChannelOption<?> option : options) {
            assertThat(option.name()).isNotBlank();
            assertThat(ChannelOption.valueOf(option.name())).isSameAs(option);
        }
    }

    @Test
    void acceptFilterExposesStableValueSemantics() {
        AcceptFilter filter = new AcceptFilter("httpready", "data=hello");
        AcceptFilter sameFilter = new AcceptFilter("httpready", "data=hello");
        AcceptFilter differentFilter = new AcceptFilter("dataready", "data=hello");

        assertThat(filter.filterName()).isEqualTo("httpready");
        assertThat(filter.filterArgs()).isEqualTo("data=hello");
        assertThat(filter).isEqualTo(sameFilter).hasSameHashCodeAs(sameFilter);
        assertThat(filter).isNotEqualTo(differentFilter);
        assertThat(filter).hasToString("httpready, data=hello");
        assertThatThrownBy(() -> new AcceptFilter(null, "args")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AcceptFilter("name", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void kqueueChannelConfigurationRoundTripsPortableOptionsWhenAvailable() {
        if (!KQueue.isAvailable()) {
            assertThatThrownBy(KQueueSocketChannel::new).isInstanceOf(LinkageError.class);
            return;
        }

        KQueueSocketChannel socketChannel = new KQueueSocketChannel();
        KQueueDomainSocketChannel domainSocketChannel = new KQueueDomainSocketChannel();
        try {
            KQueueSocketChannelConfig socketConfig = socketChannel.config();
            assertThat(socketConfig.getOptions()).containsKey(KQueueChannelOption.RCV_ALLOC_TRANSPORT_PROVIDES_GUESS);

            socketConfig.setRcvAllocTransportProvidesGuess(true);
            assertThat(socketConfig.getRcvAllocTransportProvidesGuess()).isTrue();
            assertThat(socketConfig.getOption(KQueueChannelOption.RCV_ALLOC_TRANSPORT_PROVIDES_GUESS)).isTrue();

            socketConfig.setOption(KQueueChannelOption.RCV_ALLOC_TRANSPORT_PROVIDES_GUESS, false);
            assertThat(socketConfig.getRcvAllocTransportProvidesGuess()).isFalse();
            assertThat(socketConfig.getOption(KQueueChannelOption.RCV_ALLOC_TRANSPORT_PROVIDES_GUESS)).isFalse();

            KQueueDomainSocketChannelConfig domainConfig = domainSocketChannel.config();
            domainConfig.setReadMode(DomainSocketReadMode.FILE_DESCRIPTORS);
            assertThat(domainConfig.getReadMode()).isSameAs(DomainSocketReadMode.FILE_DESCRIPTORS);
            domainConfig.setReadMode(DomainSocketReadMode.BYTES);
            assertThat(domainConfig.getReadMode()).isSameAs(DomainSocketReadMode.BYTES);
        } finally {
            domainSocketChannel.close().syncUninterruptibly();
            socketChannel.close().syncUninterruptibly();
        }
    }

    @Test
    void kqueueTcpChannelsCanExchangeBytesWhenAvailable() throws Exception {
        if (!KQueue.isAvailable()) {
            assertThatThrownBy(KQueueEventLoopGroup::new).isInstanceOf(LinkageError.class);
            return;
        }

        EventLoopGroup bossGroup = new KQueueEventLoopGroup(1);
        EventLoopGroup workerGroup = new KQueueEventLoopGroup(1);
        EventLoopGroup clientGroup = new KQueueEventLoopGroup(1);
        Channel serverChannel = null;
        try {
            CountDownLatch responseReceived = new CountDownLatch(1);
            AtomicReference<String> response = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(KQueueServerSocketChannel.class)
                    .childHandler(new EchoServerInitializer(failure));
            serverChannel = serverBootstrap.bind(new InetSocketAddress("127.0.0.1", 0)).syncUninterruptibly().channel();
            int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

            Bootstrap clientBootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channel(KQueueSocketChannel.class)
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
    void kqueueDomainSocketChannelsCanExchangeBytesWhenAvailable() throws Exception {
        if (!KQueue.isAvailable()) {
            assertThatThrownBy(KQueueEventLoopGroup::new).isInstanceOf(LinkageError.class);
            return;
        }

        Path socketPath = Files.createTempFile("netty-kqueue-domain-", ".sock");
        Files.deleteIfExists(socketPath);
        DomainSocketAddress socketAddress = new DomainSocketAddress(socketPath.toFile());
        EventLoopGroup serverGroup = new KQueueEventLoopGroup(1);
        EventLoopGroup clientGroup = new KQueueEventLoopGroup(1);
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            CountDownLatch responseReceived = new CountDownLatch(1);
            AtomicReference<String> response = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(serverGroup)
                    .channel(KQueueServerDomainSocketChannel.class)
                    .childHandler(new EchoServerInitializer(failure));
            serverChannel = serverBootstrap.bind(socketAddress).syncUninterruptibly().channel();

            Bootstrap clientBootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channel(KQueueDomainSocketChannel.class)
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
    void kqueueDatagramChannelsCanExchangePacketsWhenAvailable() throws Exception {
        if (!KQueue.isAvailable()) {
            assertThatThrownBy(KQueueEventLoopGroup::new).isInstanceOf(LinkageError.class);
            return;
        }

        EventLoopGroup serverGroup = new KQueueEventLoopGroup(1);
        EventLoopGroup clientGroup = new KQueueEventLoopGroup(1);
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            CountDownLatch responseReceived = new CountDownLatch(1);
            AtomicReference<String> response = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            Bootstrap serverBootstrap = new Bootstrap()
                    .group(serverGroup)
                    .channel(KQueueDatagramChannel.class)
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
                    .channel(KQueueDatagramChannel.class)
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
        if (KQueue.isAvailable()) {
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

    private static void assertEventLoopGroupConstructorMatchesAvailability(Supplier<? extends EventLoopGroup> constructor) {
        if (KQueue.isAvailable()) {
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
