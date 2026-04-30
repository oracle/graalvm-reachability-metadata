/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_udt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ChannelFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.udt.UdtChannel;
import io.netty.channel.udt.UdtChannelConfig;
import io.netty.channel.udt.UdtChannelOption;
import io.netty.channel.udt.UdtMessage;
import io.netty.channel.udt.UdtServerChannel;
import io.netty.channel.udt.UdtServerChannelConfig;
import io.netty.channel.udt.nio.NioUdtByteAcceptorChannel;
import io.netty.channel.udt.nio.NioUdtByteConnectorChannel;
import io.netty.channel.udt.nio.NioUdtByteRendezvousChannel;
import io.netty.channel.udt.nio.NioUdtMessageAcceptorChannel;
import io.netty.channel.udt.nio.NioUdtMessageConnectorChannel;
import io.netty.channel.udt.nio.NioUdtMessageRendezvousChannel;
import io.netty.channel.udt.nio.NioUdtProvider;
import org.junit.jupiter.api.Test;

public class Netty_transport_udtTest {
    private static final String REQUEST = "netty-udt-request";

    private static final String BYTE_RESPONSE = "byte-response";

    private static final String MESSAGE_RESPONSE = "message-response";

    private static final String RENDEZVOUS_REQUEST = "rendezvous-request";

    private static final String RENDEZVOUS_RESPONSE = "rendezvous-response";

    private static final String MESSAGE_RENDEZVOUS_REQUEST = "message-rendezvous-request";

    private static final String MESSAGE_RENDEZVOUS_RESPONSE = "message-rendezvous-response";

    @Test
    public void udtMessageSupportsCopyDuplicateReplaceAndRetainOperations() {
        UdtMessage message = new UdtMessage(Unpooled.copiedBuffer("payload", StandardCharsets.UTF_8));
        try {
            assertThat(message.content().toString(StandardCharsets.UTF_8)).isEqualTo("payload");
            assertThat(message.refCnt()).isEqualTo(1);

            UdtMessage copy = message.copy();
            try {
                message.content().setByte(0, 'P');
                assertThat(message.content().toString(StandardCharsets.UTF_8)).isEqualTo("Payload");
                assertThat(copy.content().toString(StandardCharsets.UTF_8)).isEqualTo("payload");
            } finally {
                copy.release();
            }

            UdtMessage duplicate = message.duplicate();
            duplicate.content().setByte(1, 'A');
            assertThat(message.content().toString(StandardCharsets.UTF_8)).isEqualTo("PAyload");
            assertThat(duplicate.content().toString(StandardCharsets.UTF_8)).isEqualTo("PAyload");

            UdtMessage replacement = message.replace(Unpooled.copiedBuffer("replacement", StandardCharsets.UTF_8));
            try {
                assertThat(replacement).isInstanceOf(UdtMessage.class);
                assertThat(replacement.content().toString(StandardCharsets.UTF_8)).isEqualTo("replacement");
                assertThat(message.content().toString(StandardCharsets.UTF_8)).isEqualTo("PAyload");
            } finally {
                replacement.release();
            }

            UdtMessage retainedDuplicate = message.retainedDuplicate();
            try {
                assertThat(retainedDuplicate.content().toString(StandardCharsets.UTF_8)).isEqualTo("PAyload");
                assertThat(message.refCnt()).isEqualTo(2);
            } finally {
                retainedDuplicate.release();
            }
            assertThat(message.refCnt()).isEqualTo(1);
            assertThat(message.retain().touch("integration-test")).isSameAs(message);
            assertThat(message.refCnt()).isEqualTo(2);
            message.release();
        } finally {
            assertThat(message.release()).isTrue();
        }
    }

    @Test
    public void udtChannelOptionsExposeProtocolAndSocketBufferSettings() {
        assertThat(UdtChannelOption.PROTOCOL_RECEIVE_BUFFER_SIZE.name()).endsWith("PROTOCOL_RECEIVE_BUFFER_SIZE");
        assertThat(UdtChannelOption.PROTOCOL_SEND_BUFFER_SIZE.name()).endsWith("PROTOCOL_SEND_BUFFER_SIZE");
        assertThat(UdtChannelOption.SYSTEM_RECEIVE_BUFFER_SIZE.name()).endsWith("SYSTEM_RECEIVE_BUFFER_SIZE");
        assertThat(UdtChannelOption.SYSTEM_SEND_BUFFER_SIZE.name()).endsWith("SYSTEM_SEND_BUFFER_SIZE");

        UdtChannelOption.PROTOCOL_RECEIVE_BUFFER_SIZE.validate(1_048_576);
        UdtChannelOption.PROTOCOL_SEND_BUFFER_SIZE.validate(1_048_576);
        UdtChannelOption.SYSTEM_RECEIVE_BUFFER_SIZE.validate(1_048_576);
        UdtChannelOption.SYSTEM_SEND_BUFFER_SIZE.validate(1_048_576);

        assertThatNullPointerException().isThrownBy(() -> UdtChannelOption.PROTOCOL_RECEIVE_BUFFER_SIZE.validate(null));
    }

    @Test
    public void nioUdtProviderCreatesExpectedChannelTypesAndAppliesConfiguration() {
        assertProvider((NioUdtProvider<?>) NioUdtProvider.BYTE_ACCEPTOR, "STREAM", "ACCEPTOR");
        assertProvider((NioUdtProvider<?>) NioUdtProvider.BYTE_CONNECTOR, "STREAM", "CONNECTOR");
        assertProvider((NioUdtProvider<?>) NioUdtProvider.BYTE_RENDEZVOUS, "STREAM", "RENDEZVOUS");
        assertProvider((NioUdtProvider<?>) NioUdtProvider.MESSAGE_ACCEPTOR, "DATAGRAM", "ACCEPTOR");
        assertProvider((NioUdtProvider<?>) NioUdtProvider.MESSAGE_CONNECTOR, "DATAGRAM", "CONNECTOR");
        assertProvider((NioUdtProvider<?>) NioUdtProvider.MESSAGE_RENDEZVOUS, "DATAGRAM", "RENDEZVOUS");

        assertChannelFactoryCreates(NioUdtProvider.BYTE_CONNECTOR, NioUdtByteConnectorChannel.class);
        assertChannelFactoryCreates(NioUdtProvider.BYTE_RENDEZVOUS, NioUdtByteRendezvousChannel.class);
        assertChannelFactoryCreates(NioUdtProvider.MESSAGE_CONNECTOR, NioUdtMessageConnectorChannel.class);
        assertChannelFactoryCreates(NioUdtProvider.MESSAGE_RENDEZVOUS, NioUdtMessageRendezvousChannel.class);
        assertServerChannelFactoryCreates(NioUdtProvider.BYTE_ACCEPTOR, NioUdtByteAcceptorChannel.class);
        assertServerChannelFactoryCreates(NioUdtProvider.MESSAGE_ACCEPTOR, NioUdtMessageAcceptorChannel.class);
    }

    @Test
    public void byteStreamServerAndClientExchangeDataOverUdt() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1, Executors.defaultThreadFactory(),
                NioUdtProvider.BYTE_PROVIDER);
        EventLoopGroup workerGroup = new NioEventLoopGroup(1, Executors.defaultThreadFactory(),
                NioUdtProvider.BYTE_PROVIDER);
        Channel serverChannel = null;
        Channel clientChannel = null;
        AtomicReference<String> serverReceived = new AtomicReference<>();
        AtomicReference<String> clientReceived = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch responseReceived = new CountDownLatch(1);
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channelFactory((ChannelFactory<UdtServerChannel>) NioUdtProvider.BYTE_ACCEPTOR)
                    .childHandler(new ChannelInitializer<UdtChannel>() {
                        @Override
                        protected void initChannel(UdtChannel channel) {
                            channel.pipeline().addLast(new ByteEchoServerHandler(serverReceived, failure));
                        }
                    });
            serverChannel = serverBootstrap.bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();

            Bootstrap clientBootstrap = new Bootstrap();
            clientBootstrap.group(workerGroup)
                    .channelFactory((ChannelFactory<UdtChannel>) NioUdtProvider.BYTE_CONNECTOR)
                    .handler(new ChannelInitializer<UdtChannel>() {
                        @Override
                        protected void initChannel(UdtChannel channel) {
                            channel.pipeline().addLast(
                                    new ByteClientHandler(clientReceived, failure, responseReceived));
                        }
                    });
            InetSocketAddress serverAddress = (InetSocketAddress) serverChannel.localAddress();
            clientChannel = clientBootstrap.connect("127.0.0.1", serverAddress.getPort()).sync().channel();
            clientChannel.writeAndFlush(Unpooled.copiedBuffer(REQUEST, StandardCharsets.UTF_8)).sync();

            assertThat(responseReceived.await(10, TimeUnit.SECONDS))
                    .as("client received byte-stream response")
                    .isTrue();
            assertThat(failure.get()).as("unexpected handler failure").isNull();
            assertThat(serverReceived.get()).isEqualTo(REQUEST);
            assertThat(clientReceived.get()).isEqualTo(BYTE_RESPONSE);
        } finally {
            closeChannel(clientChannel);
            closeChannel(serverChannel);
            shutdownGroup(workerGroup);
            shutdownGroup(bossGroup);
        }
    }

    @Test
    public void byteRendezvousPeersExchangeDataWithoutServerAcceptor() throws Exception {
        EventLoopGroup peerOneGroup = new NioEventLoopGroup(1, Executors.defaultThreadFactory(),
                NioUdtProvider.BYTE_PROVIDER);
        EventLoopGroup peerTwoGroup = new NioEventLoopGroup(1, Executors.defaultThreadFactory(),
                NioUdtProvider.BYTE_PROVIDER);
        Channel peerOneChannel = null;
        Channel peerTwoChannel = null;
        AtomicReference<String> peerOneReceived = new AtomicReference<>();
        AtomicReference<String> peerTwoReceived = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch messagesReceived = new CountDownLatch(2);
        try {
            InetSocketAddress peerOneAddress = availableLoopbackUdtAddress();
            InetSocketAddress peerTwoAddress = availableLoopbackUdtAddress();

            Bootstrap peerOneBootstrap = new Bootstrap();
            peerOneBootstrap.group(peerOneGroup)
                    .channelFactory((ChannelFactory<UdtChannel>) NioUdtProvider.BYTE_RENDEZVOUS)
                    .handler(new ChannelInitializer<UdtChannel>() {
                        @Override
                        protected void initChannel(UdtChannel channel) {
                            channel.pipeline().addLast(new RendezvousPeerHandler(RENDEZVOUS_RESPONSE,
                                    peerOneReceived, failure, messagesReceived));
                        }
                    });

            Bootstrap peerTwoBootstrap = new Bootstrap();
            peerTwoBootstrap.group(peerTwoGroup)
                    .channelFactory((ChannelFactory<UdtChannel>) NioUdtProvider.BYTE_RENDEZVOUS)
                    .handler(new ChannelInitializer<UdtChannel>() {
                        @Override
                        protected void initChannel(UdtChannel channel) {
                            channel.pipeline().addLast(new RendezvousPeerHandler(RENDEZVOUS_REQUEST,
                                    peerTwoReceived, failure, messagesReceived));
                        }
                    });

            ChannelFuture peerOneConnect = peerOneBootstrap.connect(peerTwoAddress, peerOneAddress);
            ChannelFuture peerTwoConnect = peerTwoBootstrap.connect(peerOneAddress, peerTwoAddress);
            peerOneChannel = peerOneConnect.sync().channel();
            peerTwoChannel = peerTwoConnect.sync().channel();

            peerOneChannel.writeAndFlush(Unpooled.copiedBuffer(RENDEZVOUS_REQUEST, StandardCharsets.UTF_8)).sync();
            peerTwoChannel.writeAndFlush(Unpooled.copiedBuffer(RENDEZVOUS_RESPONSE, StandardCharsets.UTF_8)).sync();

            assertThat(messagesReceived.await(10, TimeUnit.SECONDS)).as("both rendezvous peers received data").isTrue();
            assertThat(failure.get()).as("unexpected handler failure").isNull();
            assertThat(peerOneReceived.get()).isEqualTo(RENDEZVOUS_RESPONSE);
            assertThat(peerTwoReceived.get()).isEqualTo(RENDEZVOUS_REQUEST);
        } finally {
            closeChannel(peerOneChannel);
            closeChannel(peerTwoChannel);
            shutdownGroup(peerOneGroup);
            shutdownGroup(peerTwoGroup);
        }
    }

    @Test
    public void messageRendezvousPeersExchangeUdtMessagesWithoutServerAcceptor() throws Exception {
        EventLoopGroup peerOneGroup = new NioEventLoopGroup(1, Executors.defaultThreadFactory(),
                NioUdtProvider.MESSAGE_PROVIDER);
        EventLoopGroup peerTwoGroup = new NioEventLoopGroup(1, Executors.defaultThreadFactory(),
                NioUdtProvider.MESSAGE_PROVIDER);
        Channel peerOneChannel = null;
        Channel peerTwoChannel = null;
        AtomicReference<String> peerOneReceived = new AtomicReference<>();
        AtomicReference<String> peerTwoReceived = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch messagesReceived = new CountDownLatch(2);
        try {
            InetSocketAddress peerOneAddress = availableLoopbackUdtAddress();
            InetSocketAddress peerTwoAddress = availableLoopbackUdtAddress();

            Bootstrap peerOneBootstrap = new Bootstrap();
            peerOneBootstrap.group(peerOneGroup)
                    .channelFactory((ChannelFactory<UdtChannel>) NioUdtProvider.MESSAGE_RENDEZVOUS)
                    .handler(new ChannelInitializer<UdtChannel>() {
                        @Override
                        protected void initChannel(UdtChannel channel) {
                            channel.pipeline().addLast(new MessageRendezvousPeerHandler(MESSAGE_RENDEZVOUS_RESPONSE,
                                    peerOneReceived, failure, messagesReceived));
                        }
                    });

            Bootstrap peerTwoBootstrap = new Bootstrap();
            peerTwoBootstrap.group(peerTwoGroup)
                    .channelFactory((ChannelFactory<UdtChannel>) NioUdtProvider.MESSAGE_RENDEZVOUS)
                    .handler(new ChannelInitializer<UdtChannel>() {
                        @Override
                        protected void initChannel(UdtChannel channel) {
                            channel.pipeline().addLast(new MessageRendezvousPeerHandler(MESSAGE_RENDEZVOUS_REQUEST,
                                    peerTwoReceived, failure, messagesReceived));
                        }
                    });

            ChannelFuture peerOneConnect = peerOneBootstrap.connect(peerTwoAddress, peerOneAddress);
            ChannelFuture peerTwoConnect = peerTwoBootstrap.connect(peerOneAddress, peerTwoAddress);
            peerOneChannel = peerOneConnect.sync().channel();
            peerTwoChannel = peerTwoConnect.sync().channel();

            peerOneChannel.writeAndFlush(new UdtMessage(
                    Unpooled.copiedBuffer(MESSAGE_RENDEZVOUS_REQUEST, StandardCharsets.UTF_8))).sync();
            peerTwoChannel.writeAndFlush(new UdtMessage(
                    Unpooled.copiedBuffer(MESSAGE_RENDEZVOUS_RESPONSE, StandardCharsets.UTF_8))).sync();

            assertThat(messagesReceived.await(10, TimeUnit.SECONDS))
                    .as("both message rendezvous peers received data")
                    .isTrue();
            assertThat(failure.get()).as("unexpected handler failure").isNull();
            assertThat(peerOneReceived.get()).isEqualTo(MESSAGE_RENDEZVOUS_RESPONSE);
            assertThat(peerTwoReceived.get()).isEqualTo(MESSAGE_RENDEZVOUS_REQUEST);
        } finally {
            closeChannel(peerOneChannel);
            closeChannel(peerTwoChannel);
            shutdownGroup(peerOneGroup);
            shutdownGroup(peerTwoGroup);
        }
    }

    @Test
    public void messageServerAndClientExchangeUdtMessages() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1, Executors.defaultThreadFactory(),
                NioUdtProvider.MESSAGE_PROVIDER);
        EventLoopGroup workerGroup = new NioEventLoopGroup(1, Executors.defaultThreadFactory(),
                NioUdtProvider.MESSAGE_PROVIDER);
        Channel serverChannel = null;
        Channel clientChannel = null;
        AtomicReference<String> serverReceived = new AtomicReference<>();
        AtomicReference<String> clientReceived = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch responseReceived = new CountDownLatch(1);
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channelFactory((ChannelFactory<UdtServerChannel>) NioUdtProvider.MESSAGE_ACCEPTOR)
                    .childHandler(new ChannelInitializer<UdtChannel>() {
                        @Override
                        protected void initChannel(UdtChannel channel) {
                            channel.pipeline().addLast(new MessageEchoServerHandler(serverReceived, failure));
                        }
                    });
            serverChannel = serverBootstrap.bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();

            Bootstrap clientBootstrap = new Bootstrap();
            clientBootstrap.group(workerGroup)
                    .channelFactory((ChannelFactory<UdtChannel>) NioUdtProvider.MESSAGE_CONNECTOR)
                    .handler(new ChannelInitializer<UdtChannel>() {
                        @Override
                        protected void initChannel(UdtChannel channel) {
                            channel.pipeline().addLast(
                                    new MessageClientHandler(clientReceived, failure, responseReceived));
                        }
                    });
            InetSocketAddress serverAddress = (InetSocketAddress) serverChannel.localAddress();
            clientChannel = clientBootstrap.connect("127.0.0.1", serverAddress.getPort()).sync().channel();
            clientChannel.writeAndFlush(new UdtMessage(Unpooled.copiedBuffer(REQUEST, StandardCharsets.UTF_8))).sync();

            assertThat(responseReceived.await(10, TimeUnit.SECONDS)).as("client received message response").isTrue();
            assertThat(failure.get()).as("unexpected handler failure").isNull();
            assertThat(serverReceived.get()).isEqualTo(REQUEST);
            assertThat(clientReceived.get()).isEqualTo(MESSAGE_RESPONSE);
        } finally {
            closeChannel(clientChannel);
            closeChannel(serverChannel);
            shutdownGroup(workerGroup);
            shutdownGroup(bossGroup);
        }
    }

    private static void assertProvider(NioUdtProvider<?> provider, String expectedType, String expectedKind) {
        assertThat(provider.type().name()).isEqualTo(expectedType);
        assertThat(provider.kind().name()).isEqualTo(expectedKind);
    }

    private static void assertChannelFactoryCreates(ChannelFactory<? extends UdtChannel> factory,
            Class<? extends UdtChannel> expectedType) {
        UdtChannel channel = factory.newChannel();
        try {
            assertThat(channel).isInstanceOf(expectedType);
            assertThat(channel.isActive()).isFalse();
            assertThat(channel.localAddress()).isNull();
            assertThat(channel.remoteAddress()).isNull();
            assertThat(channel.metadata()).isNotNull();
            assertThat(NioUdtProvider.channelUDT(channel)).isNotNull();
            assertThat(NioUdtProvider.socketUDT(channel)).isNotNull();
            configure(channel.config());
        } finally {
            closeChannel(channel);
        }
    }

    private static void assertServerChannelFactoryCreates(ChannelFactory<? extends UdtServerChannel> factory,
            Class<? extends UdtServerChannel> expectedType) {
        UdtServerChannel channel = factory.newChannel();
        try {
            assertThat(channel).isInstanceOf(expectedType);
            assertThat(channel.isActive()).isFalse();
            assertThat(channel.remoteAddress()).isNull();
            assertThat(channel.metadata()).isNotNull();
            assertThat(NioUdtProvider.channelUDT(channel)).isNotNull();
            assertThat(NioUdtProvider.socketUDT(channel)).isNotNull();
            UdtServerChannelConfig config = (UdtServerChannelConfig) channel.config();
            configure(config);
            assertThat(config.setBacklog(32)).isSameAs(config);
            assertThat(config.getBacklog()).isEqualTo(32);
        } finally {
            closeChannel(channel);
        }
    }

    private static void configure(UdtChannelConfig config) {
        assertThat(config.setProtocolReceiveBufferSize(131_072)).isSameAs(config);
        assertThat(config.setProtocolSendBufferSize(262_144)).isSameAs(config);
        assertThat(config.setSystemReceiveBufferSize(524_288)).isSameAs(config);
        assertThat(config.setSystemSendBufferSize(524_288)).isSameAs(config);
        assertThat(config.setReceiveBufferSize(65_536)).isSameAs(config);
        assertThat(config.setSendBufferSize(65_536)).isSameAs(config);
        assertThat(config.setReuseAddress(true)).isSameAs(config);
        assertThat(config.setSoLinger(0)).isSameAs(config);
        assertThat(config.setOption(UdtChannelOption.PROTOCOL_RECEIVE_BUFFER_SIZE, 131_072)).isTrue();
        assertThat(config.setOption(UdtChannelOption.PROTOCOL_SEND_BUFFER_SIZE, 262_144)).isTrue();
        assertThat(config.setOption(UdtChannelOption.SYSTEM_RECEIVE_BUFFER_SIZE, 524_288)).isTrue();
        assertThat(config.setOption(UdtChannelOption.SYSTEM_SEND_BUFFER_SIZE, 524_288)).isTrue();
        assertThat(config.getProtocolReceiveBufferSize()).isEqualTo(131_072);
        assertThat(config.getProtocolSendBufferSize()).isEqualTo(262_144);
        assertThat(config.getSystemReceiveBufferSize()).isEqualTo(524_288);
        assertThat(config.getSystemSendBufferSize()).isEqualTo(524_288);
        assertThat(config.getOption(UdtChannelOption.PROTOCOL_RECEIVE_BUFFER_SIZE)).isEqualTo(131_072);
        assertThat(config.getOption(UdtChannelOption.PROTOCOL_SEND_BUFFER_SIZE)).isEqualTo(262_144);
        assertThat(config.getOption(UdtChannelOption.SYSTEM_RECEIVE_BUFFER_SIZE)).isEqualTo(524_288);
        assertThat(config.getOption(UdtChannelOption.SYSTEM_SEND_BUFFER_SIZE)).isEqualTo(524_288);
        assertThat(config.isReuseAddress()).isTrue();
        assertThat(config.getSoLinger()).isEqualTo(0);
    }

    private static InetSocketAddress availableLoopbackUdtAddress() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            return new InetSocketAddress("127.0.0.1", socket.getLocalPort());
        }
    }

    private static void closeChannel(Channel channel) {
        if (channel == null) {
            return;
        }
        if (channel.isRegistered()) {
            channel.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
            return;
        }
        try {
            NioUdtProvider.channelUDT(channel).close();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to close unregistered UDT channel", ex);
        }
    }

    private static void shutdownGroup(EventLoopGroup group) {
        group.shutdownGracefully(0, 5, TimeUnit.SECONDS).awaitUninterruptibly(10, TimeUnit.SECONDS);
    }

    private static final class ByteEchoServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final AtomicReference<String> serverReceived;

        private final AtomicReference<Throwable> failure;

        private final StringBuilder received = new StringBuilder();

        private ByteEchoServerHandler(AtomicReference<String> serverReceived, AtomicReference<Throwable> failure) {
            this.serverReceived = serverReceived;
            this.failure = failure;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
            received.append(message.toString(StandardCharsets.UTF_8));
            if (received.toString().equals(REQUEST)) {
                serverReceived.set(received.toString());
                context.writeAndFlush(Unpooled.copiedBuffer(BYTE_RESPONSE, StandardCharsets.UTF_8));
            } else if (received.length() > REQUEST.length()) {
                failure.compareAndSet(null, new AssertionError("unexpected byte request: " + received));
                context.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            failure.compareAndSet(null, cause);
            context.close();
        }
    }

    private static final class ByteClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final AtomicReference<String> clientReceived;

        private final AtomicReference<Throwable> failure;

        private final CountDownLatch responseReceived;

        private final StringBuilder received = new StringBuilder();

        private ByteClientHandler(AtomicReference<String> clientReceived, AtomicReference<Throwable> failure,
                CountDownLatch responseReceived) {
            this.clientReceived = clientReceived;
            this.failure = failure;
            this.responseReceived = responseReceived;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
            received.append(message.toString(StandardCharsets.UTF_8));
            if (received.toString().equals(BYTE_RESPONSE)) {
                clientReceived.set(received.toString());
                responseReceived.countDown();
                context.close();
            } else if (received.length() > BYTE_RESPONSE.length()) {
                failure.compareAndSet(null, new AssertionError("unexpected byte response: " + received));
                responseReceived.countDown();
                context.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            failure.compareAndSet(null, cause);
            responseReceived.countDown();
            context.close();
        }
    }

    private static final class RendezvousPeerHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final String expectedMessage;

        private final AtomicReference<String> receivedMessage;

        private final AtomicReference<Throwable> failure;

        private final CountDownLatch messagesReceived;

        private final StringBuilder received = new StringBuilder();

        private RendezvousPeerHandler(String expectedMessage, AtomicReference<String> receivedMessage,
                AtomicReference<Throwable> failure, CountDownLatch messagesReceived) {
            this.expectedMessage = expectedMessage;
            this.receivedMessage = receivedMessage;
            this.failure = failure;
            this.messagesReceived = messagesReceived;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
            received.append(message.toString(StandardCharsets.UTF_8));
            if (received.toString().equals(expectedMessage)) {
                receivedMessage.set(received.toString());
                messagesReceived.countDown();
            } else if (received.length() > expectedMessage.length()) {
                failure.compareAndSet(null, new AssertionError("unexpected rendezvous message: " + received));
                messagesReceived.countDown();
                context.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            failure.compareAndSet(null, cause);
            messagesReceived.countDown();
            context.close();
        }
    }

    private static final class MessageRendezvousPeerHandler extends SimpleChannelInboundHandler<UdtMessage> {
        private final String expectedMessage;

        private final AtomicReference<String> receivedMessage;

        private final AtomicReference<Throwable> failure;

        private final CountDownLatch messagesReceived;

        private MessageRendezvousPeerHandler(String expectedMessage, AtomicReference<String> receivedMessage,
                AtomicReference<Throwable> failure, CountDownLatch messagesReceived) {
            this.expectedMessage = expectedMessage;
            this.receivedMessage = receivedMessage;
            this.failure = failure;
            this.messagesReceived = messagesReceived;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, UdtMessage message) {
            String received = message.content().toString(StandardCharsets.UTF_8);
            if (received.equals(expectedMessage)) {
                receivedMessage.set(received);
            } else {
                failure.compareAndSet(null, new AssertionError("unexpected message rendezvous payload: " + received));
            }
            messagesReceived.countDown();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            failure.compareAndSet(null, cause);
            messagesReceived.countDown();
            context.close();
        }
    }

    private static final class MessageEchoServerHandler extends SimpleChannelInboundHandler<UdtMessage> {
        private final AtomicReference<String> serverReceived;

        private final AtomicReference<Throwable> failure;

        private MessageEchoServerHandler(AtomicReference<String> serverReceived, AtomicReference<Throwable> failure) {
            this.serverReceived = serverReceived;
            this.failure = failure;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, UdtMessage message) {
            serverReceived.set(message.content().toString(StandardCharsets.UTF_8));
            context.writeAndFlush(new UdtMessage(Unpooled.copiedBuffer(MESSAGE_RESPONSE, StandardCharsets.UTF_8)))
                    .addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            failure.compareAndSet(null, cause);
            context.close();
        }
    }

    private static final class MessageClientHandler extends SimpleChannelInboundHandler<UdtMessage> {
        private final AtomicReference<String> clientReceived;

        private final AtomicReference<Throwable> failure;

        private final CountDownLatch responseReceived;

        private MessageClientHandler(AtomicReference<String> clientReceived, AtomicReference<Throwable> failure,
                CountDownLatch responseReceived) {
            this.clientReceived = clientReceived;
            this.failure = failure;
            this.responseReceived = responseReceived;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, UdtMessage message) {
            clientReceived.set(message.content().toString(StandardCharsets.UTF_8));
            responseReceived.countDown();
            context.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            failure.compareAndSet(null, cause);
            responseReceived.countDown();
            context.close();
        }
    }
}
