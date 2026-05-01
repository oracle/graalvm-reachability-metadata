/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_transport_sctp;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.sun.nio.sctp.MessageInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.sctp.SctpChannelOption;
import io.netty.channel.sctp.SctpMessage;
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
}
