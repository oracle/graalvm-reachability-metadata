/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_udt;

import com.barchart.udt.SocketUDT;
import com.barchart.udt.TypeUDT;
import com.barchart.udt.nio.ChannelUDT;
import com.barchart.udt.nio.KindUDT;
import com.barchart.udt.nio.SelectorProviderUDT;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
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
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class Netty_transport_udtTest {
    private static final int DEFAULT_PROTOCOL_BUFFER_SIZE = 10 * 1024 * 1024;
    private static final int DEFAULT_SYSTEM_BUFFER_SIZE = 1024 * 1024;
    private static final int DEFAULT_ALLOCATOR_BUFFER_SIZE = 128 * 1024;

    @Test
    void udtMessagePreservesByteBufHolderSemantics() {
        UdtMessage message = new UdtMessage(buffer("payload"));
        UdtMessage sameValue = new UdtMessage(buffer("payload"));
        UdtMessage copy = null;
        UdtMessage duplicate = null;
        UdtMessage retainedDuplicate = null;
        UdtMessage replaced = null;
        try {
            copy = message.copy();
            duplicate = message.duplicate();
            retainedDuplicate = message.retainedDuplicate();
            replaced = message.replace(buffer("replacement"));

            assertThat(message.content().toString(StandardCharsets.UTF_8)).isEqualTo("payload");
            assertThat(message).isEqualTo(sameValue);
            assertThat(message.hashCode()).isEqualTo(sameValue.hashCode());
            assertThat(message.toString()).contains("UdtMessage");

            assertThat(copy).isNotSameAs(message).isEqualTo(message);
            assertThat(copy.content()).isNotSameAs(message.content());
            assertThat(duplicate.content()).isNotSameAs(message.content());
            assertThat(retainedDuplicate.content()).isNotSameAs(message.content());
            assertThat(retainedDuplicate.refCnt()).isEqualTo(message.refCnt());
            assertThat(replaced.content().toString(StandardCharsets.UTF_8)).isEqualTo("replacement");

            message.content().setByte(0, 'P');

            assertThat(copy.content().toString(StandardCharsets.UTF_8)).isEqualTo("payload");
            assertThat(duplicate.content().toString(StandardCharsets.UTF_8)).isEqualTo("Payload");
            assertThat(retainedDuplicate.content().toString(StandardCharsets.UTF_8)).isEqualTo("Payload");
            assertThat(replaced.content().toString(StandardCharsets.UTF_8)).isEqualTo("replacement");
        } finally {
            ReferenceCountUtil.release(replaced);
            ReferenceCountUtil.release(retainedDuplicate);
            ReferenceCountUtil.release(copy);
            ReferenceCountUtil.release(sameValue);
            ReferenceCountUtil.release(message);
        }
    }

    @Test
    void udtChannelOptionsExposeStablePublicNames() {
        assertThat(UdtChannelOption.PROTOCOL_RECEIVE_BUFFER_SIZE.name())
                .endsWith("#PROTOCOL_RECEIVE_BUFFER_SIZE");
        assertThat(UdtChannelOption.PROTOCOL_SEND_BUFFER_SIZE.name())
                .endsWith("#PROTOCOL_SEND_BUFFER_SIZE");
        assertThat(UdtChannelOption.SYSTEM_RECEIVE_BUFFER_SIZE.name())
                .endsWith("#SYSTEM_RECEIVE_BUFFER_SIZE");
        assertThat(UdtChannelOption.SYSTEM_SEND_BUFFER_SIZE.name())
                .endsWith("#SYSTEM_SEND_BUFFER_SIZE");
    }

    @Test
    void providerSingletonsAdvertiseCorrectUdtTypeAndKind() {
        assertProvider(NioUdtProvider.BYTE_ACCEPTOR, TypeUDT.STREAM, KindUDT.ACCEPTOR);
        assertProvider(NioUdtProvider.BYTE_CONNECTOR, TypeUDT.STREAM, KindUDT.CONNECTOR);
        assertProvider(NioUdtProvider.BYTE_RENDEZVOUS, TypeUDT.STREAM, KindUDT.RENDEZVOUS);
        assertProvider(NioUdtProvider.MESSAGE_ACCEPTOR, TypeUDT.DATAGRAM, KindUDT.ACCEPTOR);
        assertProvider(NioUdtProvider.MESSAGE_CONNECTOR, TypeUDT.DATAGRAM, KindUDT.CONNECTOR);
        assertProvider(NioUdtProvider.MESSAGE_RENDEZVOUS, TypeUDT.DATAGRAM, KindUDT.RENDEZVOUS);

        assertThat(NioUdtProvider.BYTE_PROVIDER).isSameAs(SelectorProviderUDT.STREAM);
        assertThat(NioUdtProvider.MESSAGE_PROVIDER).isSameAs(SelectorProviderUDT.DATAGRAM);
    }

    @Test
    void providerFactoriesCreateExpectedChannelTypesAndExposeUnderlyingUdtChannels() {
        List<Channel> channels = new ArrayList<>();
        try {
            UdtServerChannel byteAcceptor = NioUdtProvider.BYTE_ACCEPTOR.newChannel();
            channels.add(byteAcceptor);
            UdtChannel byteConnector = NioUdtProvider.BYTE_CONNECTOR.newChannel();
            channels.add(byteConnector);
            UdtChannel byteRendezvous = NioUdtProvider.BYTE_RENDEZVOUS.newChannel();
            channels.add(byteRendezvous);
            UdtServerChannel messageAcceptor = NioUdtProvider.MESSAGE_ACCEPTOR.newChannel();
            channels.add(messageAcceptor);
            UdtChannel messageConnector = NioUdtProvider.MESSAGE_CONNECTOR.newChannel();
            channels.add(messageConnector);
            UdtChannel messageRendezvous = NioUdtProvider.MESSAGE_RENDEZVOUS.newChannel();
            channels.add(messageRendezvous);

            assertThat(byteAcceptor).isInstanceOf(NioUdtByteAcceptorChannel.class);
            assertThat(byteConnector).isInstanceOf(NioUdtByteConnectorChannel.class);
            assertThat(byteRendezvous).isInstanceOf(NioUdtByteRendezvousChannel.class);
            assertThat(messageAcceptor).isInstanceOf(NioUdtMessageAcceptorChannel.class);
            assertThat(messageConnector).isInstanceOf(NioUdtMessageConnectorChannel.class);
            assertThat(messageRendezvous).isInstanceOf(NioUdtMessageRendezvousChannel.class);

            assertChannelBackedByUdt(byteAcceptor, TypeUDT.STREAM, KindUDT.ACCEPTOR);
            assertChannelBackedByUdt(byteConnector, TypeUDT.STREAM, KindUDT.CONNECTOR);
            assertChannelBackedByUdt(byteRendezvous, TypeUDT.STREAM, KindUDT.RENDEZVOUS);
            assertChannelBackedByUdt(messageAcceptor, TypeUDT.DATAGRAM, KindUDT.ACCEPTOR);
            assertChannelBackedByUdt(messageConnector, TypeUDT.DATAGRAM, KindUDT.CONNECTOR);
            assertChannelBackedByUdt(messageRendezvous, TypeUDT.DATAGRAM, KindUDT.RENDEZVOUS);
        } finally {
            closeAll(channels);
        }
    }

    @Test
    void connectorChannelConfigExposesDefaultsAndSettableOptions() {
        UdtChannel channel = NioUdtProvider.BYTE_CONNECTOR.newChannel();
        try {
            UdtChannelConfig config = channel.config();

            assertDefaultUdtConfig(config);
            assertThat(config.setOption(UdtChannelOption.PROTOCOL_RECEIVE_BUFFER_SIZE, 256 * 1024)).isTrue();
            assertThat(config.setOption(UdtChannelOption.PROTOCOL_SEND_BUFFER_SIZE, 512 * 1024)).isTrue();
            assertThat(config.setOption(ChannelOption.SO_RCVBUF, 64 * 1024)).isTrue();
            assertThat(config.setOption(ChannelOption.SO_SNDBUF, 96 * 1024)).isTrue();
            assertThat(config.setOption(ChannelOption.SO_REUSEADDR, false)).isTrue();
            assertThat(config.setOption(ChannelOption.SO_LINGER, 7)).isTrue();

            assertThat(config.getOption(UdtChannelOption.PROTOCOL_RECEIVE_BUFFER_SIZE)).isEqualTo(256 * 1024);
            assertThat(config.getOption(UdtChannelOption.PROTOCOL_SEND_BUFFER_SIZE)).isEqualTo(512 * 1024);
            assertThat(config.getOption(ChannelOption.SO_RCVBUF)).isEqualTo(64 * 1024);
            assertThat(config.getOption(ChannelOption.SO_SNDBUF)).isEqualTo(96 * 1024);
            assertThat(config.getOption(ChannelOption.SO_REUSEADDR)).isFalse();
            assertThat(config.getOption(ChannelOption.SO_LINGER)).isEqualTo(7);
            assertThat(config.getOptions()).containsKeys(
                    UdtChannelOption.PROTOCOL_RECEIVE_BUFFER_SIZE,
                    UdtChannelOption.PROTOCOL_SEND_BUFFER_SIZE,
                    UdtChannelOption.SYSTEM_RECEIVE_BUFFER_SIZE,
                    UdtChannelOption.SYSTEM_SEND_BUFFER_SIZE,
                    ChannelOption.SO_RCVBUF,
                    ChannelOption.SO_SNDBUF,
                    ChannelOption.SO_REUSEADDR,
                    ChannelOption.SO_LINGER);
        } finally {
            close(channel);
        }
    }

    @Test
    void acceptorChannelConfigAddsBacklogOptionToBaseUdtConfig() {
        UdtServerChannel channel = NioUdtProvider.MESSAGE_ACCEPTOR.newChannel();
        try {
            UdtServerChannelConfig config = (UdtServerChannelConfig) channel.config();

            assertDefaultUdtConfig(config);
            assertThat(config.getBacklog()).isEqualTo(64);
            assertThat(config.getOption(ChannelOption.SO_BACKLOG)).isEqualTo(64);
            assertThat(config.setOption(ChannelOption.SO_BACKLOG, 128)).isTrue();
            assertThat(config.getBacklog()).isEqualTo(128);
            assertThat(config.getOptions()).containsKeys(ChannelOption.SO_BACKLOG);
        } finally {
            close(channel);
        }
    }

    @Test
    void providersOpenAndCloseSelectorsForByteAndMessageTransports() throws IOException {
        Selector byteSelector = NioUdtProvider.BYTE_PROVIDER.openSelector();
        try {
            assertThat(byteSelector.isOpen()).isTrue();
            assertThat(byteSelector.provider()).isSameAs(NioUdtProvider.BYTE_PROVIDER);
        } finally {
            byteSelector.close();
        }

        Selector messageSelector = NioUdtProvider.MESSAGE_PROVIDER.openSelector();
        try {
            assertThat(messageSelector.isOpen()).isTrue();
            assertThat(messageSelector.provider()).isSameAs(NioUdtProvider.MESSAGE_PROVIDER);
        } finally {
            messageSelector.close();
        }
    }

    @Test
    void nonUdtChannelsAreNotUnwrappedAsUdtChannels() {
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        try {
            assertThat(NioUdtProvider.channelUDT(embeddedChannel)).isNull();
            assertThat(NioUdtProvider.socketUDT(embeddedChannel)).isNull();
        } finally {
            embeddedChannel.finishAndReleaseAll();
        }
    }

    @Test
    void byteTransportBootstrapsClientServerAndTransfersPayload() throws InterruptedException {
        EventLoopGroup serverGroup = new NioEventLoopGroup(1, (ThreadFactory) null, NioUdtProvider.BYTE_PROVIDER);
        EventLoopGroup clientGroup = new NioEventLoopGroup(1, (ThreadFactory) null, NioUdtProvider.BYTE_PROVIDER);
        AtomicReference<ByteBuf> receivedPayload = new AtomicReference<>();
        CountDownLatch payloadReceived = new CountDownLatch(1);
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(serverGroup, serverGroup)
                    .channelFactory(NioUdtProvider.BYTE_ACCEPTOR)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
                                    receivedPayload.set(message.retainedDuplicate());
                                    payloadReceived.countDown();
                                }
                            });
                        }
                    });
            serverChannel = serverBootstrap.bind(new InetSocketAddress("127.0.0.1", 0))
                    .syncUninterruptibly()
                    .channel();

            Bootstrap clientBootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channelFactory(NioUdtProvider.BYTE_CONNECTOR)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                        }
                    });
            InetSocketAddress serverAddress = (InetSocketAddress) serverChannel.localAddress();
            clientChannel = clientBootstrap.connect(serverAddress).syncUninterruptibly().channel();
            clientChannel.writeAndFlush(buffer("udt-byte-transport")).syncUninterruptibly();

            assertThat(payloadReceived.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedPayload.get().toString(StandardCharsets.UTF_8)).isEqualTo("udt-byte-transport");
        } finally {
            ReferenceCountUtil.release(receivedPayload.get());
            closeRegistered(clientChannel);
            closeRegistered(serverChannel);
            serverGroup.shutdownGracefully().syncUninterruptibly();
            clientGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void messageTransportBootstrapsClientServerAndPreservesMessageFrames() throws InterruptedException {
        EventLoopGroup serverGroup = new NioEventLoopGroup(1, (ThreadFactory) null, NioUdtProvider.MESSAGE_PROVIDER);
        EventLoopGroup clientGroup = new NioEventLoopGroup(1, (ThreadFactory) null, NioUdtProvider.MESSAGE_PROVIDER);
        List<ByteBuf> receivedMessages = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch messagesReceived = new CountDownLatch(2);
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(serverGroup, serverGroup)
                    .channelFactory(NioUdtProvider.MESSAGE_ACCEPTOR)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            channel.pipeline().addLast(new SimpleChannelInboundHandler<UdtMessage>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext context, UdtMessage message) {
                                    receivedMessages.add(message.content().retainedDuplicate());
                                    messagesReceived.countDown();
                                }
                            });
                        }
                    });
            serverChannel = serverBootstrap.bind(new InetSocketAddress("127.0.0.1", 0))
                    .syncUninterruptibly()
                    .channel();

            Bootstrap clientBootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channelFactory(NioUdtProvider.MESSAGE_CONNECTOR)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                        }
                    });
            InetSocketAddress serverAddress = (InetSocketAddress) serverChannel.localAddress();
            clientChannel = clientBootstrap.connect(serverAddress).syncUninterruptibly().channel();
            clientChannel.writeAndFlush(new UdtMessage(buffer("first-message-frame"))).syncUninterruptibly();
            clientChannel.writeAndFlush(new UdtMessage(buffer("second-message-frame"))).syncUninterruptibly();

            assertThat(messagesReceived.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedMessages)
                    .extracting(message -> message.toString(StandardCharsets.UTF_8))
                    .containsExactly("first-message-frame", "second-message-frame");
        } finally {
            for (ByteBuf message : receivedMessages) {
                ReferenceCountUtil.release(message);
            }
            closeRegistered(clientChannel);
            closeRegistered(serverChannel);
            serverGroup.shutdownGracefully().syncUninterruptibly();
            clientGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static ByteBuf buffer(String value) {
        return Unpooled.copiedBuffer(value, StandardCharsets.UTF_8);
    }

    private static void assertProvider(
            ChannelFactory<?> factory,
            TypeUDT expectedType,
            KindUDT expectedKind) {
        assertThat(factory).isInstanceOf(NioUdtProvider.class);
        NioUdtProvider<?> provider = (NioUdtProvider<?>) factory;

        assertThat(provider.type()).isEqualTo(expectedType);
        assertThat(provider.kind()).isEqualTo(expectedKind);
    }

    private static void assertChannelBackedByUdt(
            UdtChannel channel,
            TypeUDT expectedType,
            KindUDT expectedKind) {
        ChannelUDT channelUDT = NioUdtProvider.channelUDT(channel);
        SocketUDT socketUDT = NioUdtProvider.socketUDT(channel);

        assertThat(channelUDT).isNotNull();
        assertThat(channelUDT.typeUDT()).isEqualTo(expectedType);
        assertThat(channelUDT.kindUDT()).isEqualTo(expectedKind);
        assertThat(channelUDT.isOpen()).isTrue();
        assertThat(socketUDT).isNotNull();
        assertThat(socketUDT.type()).isEqualTo(expectedType);
        assertThat(socketUDT.isOpen()).isTrue();
        assertThat(channel.isActive()).isFalse();
    }

    private static void assertDefaultUdtConfig(UdtChannelConfig config) {
        assertThat(config.getProtocolReceiveBufferSize()).isEqualTo(DEFAULT_PROTOCOL_BUFFER_SIZE);
        assertThat(config.getProtocolSendBufferSize()).isEqualTo(DEFAULT_PROTOCOL_BUFFER_SIZE);
        assertThat(config.getSystemReceiveBufferSize()).isEqualTo(DEFAULT_SYSTEM_BUFFER_SIZE);
        assertThat(config.getSystemSendBufferSize()).isEqualTo(DEFAULT_SYSTEM_BUFFER_SIZE);
        assertThat(config.getReceiveBufferSize()).isEqualTo(DEFAULT_ALLOCATOR_BUFFER_SIZE);
        assertThat(config.getSendBufferSize()).isEqualTo(DEFAULT_ALLOCATOR_BUFFER_SIZE);
        assertThat(config.isReuseAddress()).isTrue();
        assertThat(config.getSoLinger()).isZero();
    }

    private static void closeAll(List<Channel> channels) {
        for (Channel channel : channels) {
            close(channel);
        }
    }

    private static void closeRegistered(Channel channel) {
        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
    }

    private static void close(Channel channel) {
        if (channel == null) {
            return;
        }

        ChannelUDT channelUDT = NioUdtProvider.channelUDT(channel);
        if (channelUDT == null) {
            channel.close().syncUninterruptibly();
            return;
        }

        try {
            channelUDT.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close UDT channel", e);
        }
    }
}
