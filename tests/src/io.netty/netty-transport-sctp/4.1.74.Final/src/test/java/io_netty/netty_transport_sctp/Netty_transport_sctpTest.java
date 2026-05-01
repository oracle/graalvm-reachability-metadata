/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_sctp;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.nio.sctp.Association;
import com.sun.nio.sctp.AssociationChangeNotification;
import com.sun.nio.sctp.HandlerResult;
import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.PeerAddressChangeNotification;
import com.sun.nio.sctp.SctpStandardSocketOptions.InitMaxStreams;
import com.sun.nio.sctp.SendFailedNotification;
import com.sun.nio.sctp.ShutdownNotification;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.sctp.SctpChannel;
import io.netty.channel.sctp.SctpChannelConfig;
import io.netty.channel.sctp.SctpChannelOption;
import io.netty.channel.sctp.SctpMessage;
import io.netty.channel.sctp.SctpNotificationHandler;
import io.netty.channel.sctp.SctpServerChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.sctp.SctpInboundByteStreamHandler;
import io.netty.handler.codec.sctp.SctpMessageCompletionHandler;
import io.netty.handler.codec.sctp.SctpMessageToMessageDecoder;
import io.netty.handler.codec.sctp.SctpOutboundByteStreamHandler;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Netty_transport_sctpTest {
    private static final int PROTOCOL_IDENTIFIER = 51;
    private static final int STREAM_IDENTIFIER = 7;

    @Test
    void sctpMessageConstructedFromIdentifiersPreservesMetadataAndBufferSemantics() {
        SctpMessage message = new SctpMessage(
                PROTOCOL_IDENTIFIER,
                STREAM_IDENTIFIER,
                true,
                buffer("payload"));
        SctpMessage sameValue = new SctpMessage(
                PROTOCOL_IDENTIFIER,
                STREAM_IDENTIFIER,
                true,
                buffer("payload"));
        SctpMessage copy = null;
        SctpMessage duplicate = null;
        SctpMessage retainedDuplicate = null;
        SctpMessage replaced = null;
        try {
            copy = message.copy();
            duplicate = message.duplicate();
            retainedDuplicate = message.retainedDuplicate();
            replaced = message.replace(buffer("replacement"));

            assertThat(message.protocolIdentifier()).isEqualTo(PROTOCOL_IDENTIFIER);
            assertThat(message.streamIdentifier()).isEqualTo(STREAM_IDENTIFIER);
            assertThat(message.isUnordered()).isTrue();
            assertThat(message.isComplete()).isTrue();
            assertThat(message.messageInfo()).isNull();
            assertThat(message.content().toString(StandardCharsets.UTF_8)).isEqualTo("payload");
            assertThat(message).isEqualTo(sameValue);
            assertThat(message.hashCode()).isEqualTo(sameValue.hashCode());
            assertThat(message.toString())
                    .contains("streamIdentifier=" + STREAM_IDENTIFIER)
                    .contains("protocolIdentifier=" + PROTOCOL_IDENTIFIER)
                    .contains("unordered=true")
                    .contains("data=");

            assertThat(copy).isNotSameAs(message).isEqualTo(message);
            assertThat(copy.content()).isNotSameAs(message.content());
            assertThat(duplicate.content()).isNotSameAs(message.content());
            assertThat(duplicate.content().toString(StandardCharsets.UTF_8)).isEqualTo("payload");
            assertThat(retainedDuplicate.content().toString(StandardCharsets.UTF_8)).isEqualTo("payload");
            assertThat(replaced.protocolIdentifier()).isEqualTo(PROTOCOL_IDENTIFIER);
            assertThat(replaced.streamIdentifier()).isEqualTo(STREAM_IDENTIFIER);
            assertThat(replaced.isUnordered()).isTrue();
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
    void sctpMessageConstructedFromMessageInfoReflectsJdkSctpFlags() {
        MessageInfo messageInfo = messageInfo(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER, false, true);
        SctpMessage message = new SctpMessage(messageInfo, buffer("fragment"));
        SctpMessage replaced = null;
        try {
            replaced = message.replace(buffer("new-data"));

            assertThat(message.messageInfo()).isSameAs(messageInfo);
            assertThat(message.protocolIdentifier()).isEqualTo(PROTOCOL_IDENTIFIER);
            assertThat(message.streamIdentifier()).isEqualTo(STREAM_IDENTIFIER);
            assertThat(message.isUnordered()).isTrue();
            assertThat(message.isComplete()).isFalse();
            assertThat(replaced.messageInfo()).isSameAs(messageInfo);
            assertThat(replaced.isComplete()).isFalse();
            assertThat(replaced.content().toString(StandardCharsets.UTF_8)).isEqualTo("new-data");
        } finally {
            ReferenceCountUtil.release(replaced);
            ReferenceCountUtil.release(message);
        }
    }

    @Test
    void outboundByteStreamHandlerWrapsByteBufInSctpMessage() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new SctpOutboundByteStreamHandler(STREAM_IDENTIFIER, PROTOCOL_IDENTIFIER, true));
        SctpMessage outbound = null;
        ByteBuf payload = buffer("outbound-data");
        try {
            assertThat(channel.writeOutbound(payload)).isTrue();
            payload = null;
            outbound = channel.readOutbound();

            assertThat(outbound.protocolIdentifier()).isEqualTo(PROTOCOL_IDENTIFIER);
            assertThat(outbound.streamIdentifier()).isEqualTo(STREAM_IDENTIFIER);
            assertThat(outbound.isUnordered()).isTrue();
            assertThat(outbound.isComplete()).isTrue();
            assertThat(outbound.content().toString(StandardCharsets.UTF_8)).isEqualTo("outbound-data");
            assertThat(channel.finish()).isFalse();
        } finally {
            ReferenceCountUtil.release(outbound);
            ReferenceCountUtil.release(payload);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void inboundByteStreamHandlerAcceptsOnlyConfiguredProtocolAndStream() throws Exception {
        SctpInboundByteStreamHandler handler = new SctpInboundByteStreamHandler(
                PROTOCOL_IDENTIFIER,
                STREAM_IDENTIFIER);
        SctpMessage matching = new SctpMessage(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER, buffer("match"));
        SctpMessage wrongProtocol = new SctpMessage(
                PROTOCOL_IDENTIFIER + 1,
                STREAM_IDENTIFIER,
                buffer("wrong-protocol"));
        SctpMessage wrongStream = new SctpMessage(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER + 1, buffer("wrong-stream"));
        try {
            assertThat(handler.acceptInboundMessage(matching)).isTrue();
            assertThat(handler.acceptInboundMessage(wrongProtocol)).isFalse();
            assertThat(handler.acceptInboundMessage(wrongStream)).isFalse();
            assertThat(handler.acceptInboundMessage("not-an-sctp-message")).isFalse();
        } finally {
            ReferenceCountUtil.release(matching);
            ReferenceCountUtil.release(wrongProtocol);
            ReferenceCountUtil.release(wrongStream);
        }
    }

    @Test
    void inboundByteStreamHandlerExtractsContentFromCompleteMatchingMessages() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new SctpInboundByteStreamHandler(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER));
        SctpMessage inbound = new SctpMessage(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER, buffer("inbound-data"));
        ByteBuf decoded = null;
        try {
            assertThat(channel.writeInbound(inbound)).isTrue();
            inbound = null;
            decoded = channel.readInbound();

            assertThat(decoded.toString(StandardCharsets.UTF_8)).isEqualTo("inbound-data");
            assertThat(channel.finish()).isFalse();
        } finally {
            ReferenceCountUtil.release(decoded);
            ReferenceCountUtil.release(inbound);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void inboundByteStreamHandlerRejectsIncompleteMessagesUntilCompletionHandlerIsInstalled() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new SctpInboundByteStreamHandler(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER));
        SctpMessage incomplete = new SctpMessage(
                messageInfo(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER, false, false),
                buffer("partial"));
        try {
            SctpMessage message = incomplete;
            assertThatThrownBy(() -> channel.writeInbound(message))
                    .isInstanceOf(CodecException.class)
                    .hasMessageContaining(SctpMessageCompletionHandler.class.getSimpleName());
            incomplete = null;
        } finally {
            ReferenceCountUtil.release(incomplete);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void completionHandlerAggregatesFragmentsPerStreamBeforeEmittingMessage() {
        EmbeddedChannel channel = new EmbeddedChannel(new SctpMessageCompletionHandler());
        SctpMessage first = new SctpMessage(
                messageInfo(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER, false, false),
                buffer("hello "));
        SctpMessage interleaved = new SctpMessage(
                messageInfo(PROTOCOL_IDENTIFIER + 1, STREAM_IDENTIFIER + 1, false, false),
                buffer("other-"));
        SctpMessage second = new SctpMessage(
                messageInfo(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER, true, true),
                buffer("world"));
        SctpMessage completed = null;
        try {
            assertThat(channel.writeInbound(first)).isFalse();
            first = null;
            assertThat((Object) channel.readInbound()).isNull();

            assertThat(channel.writeInbound(interleaved)).isFalse();
            interleaved = null;
            assertThat((Object) channel.readInbound()).isNull();

            assertThat(channel.writeInbound(second)).isTrue();
            second = null;
            completed = channel.readInbound();

            assertThat(completed.protocolIdentifier()).isEqualTo(PROTOCOL_IDENTIFIER);
            assertThat(completed.streamIdentifier()).isEqualTo(STREAM_IDENTIFIER);
            assertThat(completed.isUnordered()).isTrue();
            assertThat(completed.isComplete()).isTrue();
            assertThat(completed.content().toString(StandardCharsets.UTF_8)).isEqualTo("hello world");
            assertThat(channel.finish()).isFalse();
        } finally {
            ReferenceCountUtil.release(completed);
            ReferenceCountUtil.release(second);
            ReferenceCountUtil.release(interleaved);
            ReferenceCountUtil.release(first);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void completionHandlerReleasesBufferedFragmentsWhenRemoved() {
        EmbeddedChannel channel = new EmbeddedChannel(new SctpMessageCompletionHandler());
        ByteBuf fragmentContent = buffer("orphaned-fragment");
        SctpMessage incomplete = new SctpMessage(
                messageInfo(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER, false, false),
                fragmentContent);
        try {
            assertThat(channel.writeInbound(incomplete)).isFalse();
            incomplete = null;
            assertThat(fragmentContent.refCnt()).isOne();

            channel.pipeline().remove(SctpMessageCompletionHandler.class);

            assertThat(fragmentContent.refCnt()).isZero();
            assertThat(channel.finish()).isFalse();
        } finally {
            ReferenceCountUtil.release(incomplete);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void completionHandlerPassesAlreadyCompleteMessagesThrough() {
        EmbeddedChannel channel = new EmbeddedChannel(new SctpMessageCompletionHandler());
        SctpMessage complete = new SctpMessage(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER, true, buffer("complete"));
        SctpMessage passedThrough = null;
        try {
            assertThat(channel.writeInbound(complete)).isTrue();
            complete = null;
            passedThrough = channel.readInbound();

            assertThat(passedThrough.protocolIdentifier()).isEqualTo(PROTOCOL_IDENTIFIER);
            assertThat(passedThrough.streamIdentifier()).isEqualTo(STREAM_IDENTIFIER);
            assertThat(passedThrough.isUnordered()).isTrue();
            assertThat(passedThrough.content().toString(StandardCharsets.UTF_8)).isEqualTo("complete");
            assertThat(channel.finish()).isFalse();
        } finally {
            ReferenceCountUtil.release(passedThrough);
            ReferenceCountUtil.release(complete);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void sctpMessageToMessageDecoderProcessesOnlyCompleteSctpMessages() throws Exception {
        Utf8Decoder decoder = new Utf8Decoder();
        SctpMessage complete = new SctpMessage(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER, buffer("decoded"));
        SctpMessage incomplete = new SctpMessage(
                messageInfo(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER, false, false),
                buffer("partial"));
        try {
            assertThat(decoder.acceptInboundMessage(complete)).isTrue();
            assertThat(decoder.acceptInboundMessage("plain-bytes")).isFalse();
            assertThatThrownBy(() -> decoder.acceptInboundMessage(incomplete))
                    .isInstanceOf(CodecException.class)
                    .hasMessageContaining(SctpMessageCompletionHandler.class.getSimpleName());
        } finally {
            ReferenceCountUtil.release(complete);
            ReferenceCountUtil.release(incomplete);
        }
    }

    @Test
    void sctpMessageToMessageDecoderCanTransformSctpMessagesInPipeline() {
        EmbeddedChannel channel = new EmbeddedChannel(new Utf8Decoder());
        SctpMessage inbound = new SctpMessage(PROTOCOL_IDENTIFIER, STREAM_IDENTIFIER, buffer("decoded-text"));
        try {
            assertThat(channel.writeInbound(inbound)).isTrue();
            inbound = null;
            assertThat((String) channel.readInbound()).isEqualTo("decoded-text");
            assertThat(channel.finish()).isFalse();
        } finally {
            ReferenceCountUtil.release(inbound);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void sctpChannelOptionsExposeStablePublicOptionNames() {
        assertThat(SctpChannelOption.SCTP_DISABLE_FRAGMENTS.name()).endsWith("#SCTP_DISABLE_FRAGMENTS");
        assertThat(SctpChannelOption.SCTP_EXPLICIT_COMPLETE.name()).endsWith("#SCTP_EXPLICIT_COMPLETE");
        assertThat(SctpChannelOption.SCTP_FRAGMENT_INTERLEAVE.name()).endsWith("#SCTP_FRAGMENT_INTERLEAVE");
        assertThat(SctpChannelOption.SCTP_INIT_MAXSTREAMS.name()).endsWith("#SCTP_INIT_MAXSTREAMS");
        assertThat(SctpChannelOption.SCTP_NODELAY.name()).endsWith("#SCTP_NODELAY");
        assertThat(SctpChannelOption.SCTP_PRIMARY_ADDR.name()).endsWith("#SCTP_PRIMARY_ADDR");
        assertThat(SctpChannelOption.SCTP_SET_PEER_PRIMARY_ADDR.name()).endsWith("#SCTP_SET_PEER_PRIMARY_ADDR");
    }

    @Test
    void notificationHandlerForwardsSctpNotificationsAndClosesOnShutdown() {
        UserEventCollector eventCollector = new UserEventCollector();
        TestSctpChannel channel = new TestSctpChannel(eventCollector);
        SctpNotificationHandler handler = new SctpNotificationHandler(channel);
        Association association = new TestAssociation(1, 2, 3);
        AssociationChangeNotification associationChange = new TestAssociationChangeNotification(association);
        PeerAddressChangeNotification peerAddressChange = new TestPeerAddressChangeNotification(
                association,
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 4567));
        SendFailedNotification sendFailed = new TestSendFailedNotification(
                association,
                peerAddressChange.address(),
                ByteBuffer.wrap(new byte[] {1, 2, 3}),
                99,
                STREAM_IDENTIFIER);
        ShutdownNotification shutdown = new TestShutdownNotification(association);
        try {
            assertThat(handler.handleNotification(associationChange, null)).isEqualTo(HandlerResult.CONTINUE);
            assertThat(handler.handleNotification(peerAddressChange, null)).isEqualTo(HandlerResult.CONTINUE);
            assertThat(handler.handleNotification(sendFailed, null)).isEqualTo(HandlerResult.CONTINUE);
            assertThat(handler.handleNotification(shutdown, null)).isEqualTo(HandlerResult.RETURN);

            assertThat(eventCollector.events())
                    .containsExactly(associationChange, peerAddressChange, sendFailed, shutdown);
            assertThat(channel.isOpen()).isFalse();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static MessageInfo messageInfo(
            int protocolIdentifier,
            int streamIdentifier,
            boolean complete,
            boolean unordered) {
        return MessageInfo.createOutgoing(null, streamIdentifier)
                .payloadProtocolID(protocolIdentifier)
                .complete(complete)
                .unordered(unordered);
    }

    private static ByteBuf buffer(String value) {
        return Unpooled.copiedBuffer(value, StandardCharsets.UTF_8);
    }

    private static final class Utf8Decoder extends SctpMessageToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, SctpMessage msg, List<Object> out) {
            out.add(msg.content().toString(StandardCharsets.UTF_8));
        }
    }

    private static final class UserEventCollector extends ChannelInboundHandlerAdapter {
        private final List<Object> events = new ArrayList<>();

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            events.add(evt);
        }

        private List<Object> events() {
            return events;
        }
    }

    private static final class TestSctpChannel extends EmbeddedChannel implements SctpChannel {
        private TestSctpChannel(ChannelInboundHandlerAdapter handler) {
            super(DefaultChannelId.newInstance(), false, new TestSctpChannelConfig(), handler);
        }

        @Override
        public SctpServerChannel parent() {
            return null;
        }

        @Override
        public Association association() {
            return null;
        }

        @Override
        public InetSocketAddress localAddress() {
            return null;
        }

        @Override
        public Set<InetSocketAddress> allLocalAddresses() {
            return Set.of();
        }

        @Override
        public SctpChannelConfig config() {
            return (SctpChannelConfig) super.config();
        }

        @Override
        public InetSocketAddress remoteAddress() {
            return null;
        }

        @Override
        public Set<InetSocketAddress> allRemoteAddresses() {
            return Set.of();
        }

        @Override
        public ChannelFuture bindAddress(InetAddress localAddress) {
            return bindAddress(localAddress, newPromise());
        }

        @Override
        public ChannelFuture bindAddress(InetAddress localAddress, ChannelPromise promise) {
            return promise.setSuccess();
        }

        @Override
        public ChannelFuture unbindAddress(InetAddress localAddress) {
            return unbindAddress(localAddress, newPromise());
        }

        @Override
        public ChannelFuture unbindAddress(InetAddress localAddress, ChannelPromise promise) {
            return promise.setSuccess();
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private static final class TestSctpChannelConfig implements SctpChannelConfig {
        private boolean autoRead = true;
        private boolean autoClose = true;
        private int connectTimeoutMillis = 30000;
        private int maxMessagesPerRead = 1;
        private int writeSpinCount = 16;
        private int sendBufferSize = 8192;
        private int receiveBufferSize = 8192;
        private InitMaxStreams initMaxStreams = InitMaxStreams.create(1, 1);
        private RecvByteBufAllocator recvByteBufAllocator = new FixedRecvByteBufAllocator(2048);
        private WriteBufferWaterMark writeBufferWaterMark = WriteBufferWaterMark.DEFAULT;
        private MessageSizeEstimator messageSizeEstimator = DefaultMessageSizeEstimator.DEFAULT;

        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            return Map.of();
        }

        @Override
        public boolean setOptions(Map<ChannelOption<?>, ?> options) {
            return true;
        }

        @Override
        public <T> T getOption(ChannelOption<T> option) {
            return null;
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            return true;
        }

        @Override
        public boolean isSctpNoDelay() {
            return false;
        }

        @Override
        public SctpChannelConfig setSctpNoDelay(boolean sctpNoDelay) {
            return this;
        }

        @Override
        public int getSendBufferSize() {
            return sendBufferSize;
        }

        @Override
        public SctpChannelConfig setSendBufferSize(int sendBufferSize) {
            this.sendBufferSize = sendBufferSize;
            return this;
        }

        @Override
        public int getReceiveBufferSize() {
            return receiveBufferSize;
        }

        @Override
        public SctpChannelConfig setReceiveBufferSize(int receiveBufferSize) {
            this.receiveBufferSize = receiveBufferSize;
            return this;
        }

        @Override
        public InitMaxStreams getInitMaxStreams() {
            return initMaxStreams;
        }

        @Override
        public SctpChannelConfig setInitMaxStreams(InitMaxStreams initMaxStreams) {
            this.initMaxStreams = initMaxStreams;
            return this;
        }

        @Override
        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        @Override
        public SctpChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
            return this;
        }

        @Override
        public int getMaxMessagesPerRead() {
            return maxMessagesPerRead;
        }

        @Override
        public SctpChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
            this.maxMessagesPerRead = maxMessagesPerRead;
            return this;
        }

        @Override
        public int getWriteSpinCount() {
            return writeSpinCount;
        }

        @Override
        public SctpChannelConfig setWriteSpinCount(int writeSpinCount) {
            this.writeSpinCount = writeSpinCount;
            return this;
        }

        @Override
        public ByteBufAllocator getAllocator() {
            return ByteBufAllocator.DEFAULT;
        }

        @Override
        public SctpChannelConfig setAllocator(ByteBufAllocator allocator) {
            return this;
        }

        @Override
        public <T extends RecvByteBufAllocator> T getRecvByteBufAllocator() {
            return (T) recvByteBufAllocator;
        }

        @Override
        public SctpChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
            recvByteBufAllocator = allocator;
            return this;
        }

        @Override
        public boolean isAutoRead() {
            return autoRead;
        }

        @Override
        public SctpChannelConfig setAutoRead(boolean autoRead) {
            this.autoRead = autoRead;
            return this;
        }

        @Override
        public boolean isAutoClose() {
            return autoClose;
        }

        @Override
        public SctpChannelConfig setAutoClose(boolean autoClose) {
            this.autoClose = autoClose;
            return this;
        }

        @Override
        public int getWriteBufferHighWaterMark() {
            return writeBufferWaterMark.high();
        }

        @Override
        public SctpChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
            writeBufferWaterMark = new WriteBufferWaterMark(getWriteBufferLowWaterMark(), writeBufferHighWaterMark);
            return this;
        }

        @Override
        public int getWriteBufferLowWaterMark() {
            return writeBufferWaterMark.low();
        }

        @Override
        public SctpChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
            writeBufferWaterMark = new WriteBufferWaterMark(writeBufferLowWaterMark, getWriteBufferHighWaterMark());
            return this;
        }

        @Override
        public WriteBufferWaterMark getWriteBufferWaterMark() {
            return writeBufferWaterMark;
        }

        @Override
        public SctpChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
            this.writeBufferWaterMark = writeBufferWaterMark;
            return this;
        }

        @Override
        public MessageSizeEstimator getMessageSizeEstimator() {
            return messageSizeEstimator;
        }

        @Override
        public SctpChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
            messageSizeEstimator = estimator;
            return this;
        }
    }

    private static final class TestAssociation extends Association {
        private TestAssociation(int associationId, int maxInboundStreams, int maxOutboundStreams) {
            super(associationId, maxInboundStreams, maxOutboundStreams);
        }
    }

    private static final class TestAssociationChangeNotification extends AssociationChangeNotification {
        private final Association association;

        private TestAssociationChangeNotification(Association association) {
            this.association = association;
        }

        @Override
        public Association association() {
            return association;
        }

        @Override
        public AssocChangeEvent event() {
            return AssocChangeEvent.COMM_UP;
        }
    }

    private static final class TestPeerAddressChangeNotification extends PeerAddressChangeNotification {
        private final Association association;
        private final SocketAddress address;

        private TestPeerAddressChangeNotification(Association association, SocketAddress address) {
            this.association = association;
            this.address = address;
        }

        @Override
        public SocketAddress address() {
            return address;
        }

        @Override
        public Association association() {
            return association;
        }

        @Override
        public AddressChangeEvent event() {
            return AddressChangeEvent.ADDR_AVAILABLE;
        }
    }

    private static final class TestSendFailedNotification extends SendFailedNotification {
        private final Association association;
        private final SocketAddress address;
        private final ByteBuffer buffer;
        private final int errorCode;
        private final int streamNumber;

        private TestSendFailedNotification(
                Association association,
                SocketAddress address,
                ByteBuffer buffer,
                int errorCode,
                int streamNumber) {
            this.association = association;
            this.address = address;
            this.buffer = buffer;
            this.errorCode = errorCode;
            this.streamNumber = streamNumber;
        }

        @Override
        public Association association() {
            return association;
        }

        @Override
        public SocketAddress address() {
            return address;
        }

        @Override
        public ByteBuffer buffer() {
            return buffer;
        }

        @Override
        public int errorCode() {
            return errorCode;
        }

        @Override
        public int streamNumber() {
            return streamNumber;
        }
    }

    private static final class TestShutdownNotification extends ShutdownNotification {
        private final Association association;

        private TestShutdownNotification(Association association) {
            this.association = association;
        }

        @Override
        public Association association() {
            return association;
        }
    }
}
