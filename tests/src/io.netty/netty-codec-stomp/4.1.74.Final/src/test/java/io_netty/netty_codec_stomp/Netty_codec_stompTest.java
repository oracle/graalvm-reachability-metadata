/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_stomp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.stomp.DefaultLastStompContentSubframe;
import io.netty.handler.codec.stomp.DefaultStompContentSubframe;
import io.netty.handler.codec.stomp.DefaultStompFrame;
import io.netty.handler.codec.stomp.DefaultStompHeaders;
import io.netty.handler.codec.stomp.DefaultStompHeadersSubframe;
import io.netty.handler.codec.stomp.LastStompContentSubframe;
import io.netty.handler.codec.stomp.StompCommand;
import io.netty.handler.codec.stomp.StompContentSubframe;
import io.netty.handler.codec.stomp.StompFrame;
import io.netty.handler.codec.stomp.StompHeaders;
import io.netty.handler.codec.stomp.StompHeadersSubframe;
import io.netty.handler.codec.stomp.StompSubframeAggregator;
import io.netty.handler.codec.stomp.StompSubframeDecoder;
import io.netty.handler.codec.stomp.StompSubframeEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Netty_codec_stompTest {
    private static final Charset UTF_8 = CharsetUtil.UTF_8;

    @Test
    void headersStoreMultipleValuesAndSupportStringViews() {
        DefaultStompHeaders headers = new DefaultStompHeaders();

        headers.add(StompHeaders.ACCEPT_VERSION, "1.1");
        headers.add(StompHeaders.ACCEPT_VERSION, "1.2");
        headers.set(StompHeaders.HOST, "broker.example");
        headers.add("case-sensitive", "Value");

        assertThat(headers.getAsString(StompHeaders.HOST)).isEqualTo("broker.example");
        assertThat(headers.getAllAsString(StompHeaders.ACCEPT_VERSION)).containsExactly("1.1", "1.2");
        assertThat(headers.contains("case-sensitive", "value")).isFalse();
        assertThat(headers.contains("case-sensitive", "value", true)).isTrue();
        List<String> entries = new ArrayList<>();
        headers.iteratorAsString().forEachRemaining(entry -> entries.add(entry.getKey() + "=" + entry.getValue()));
        assertThat(entries).contains(
                "accept-version=1.1",
                "accept-version=1.2",
                "host=broker.example");
    }

    @Test
    void copiedHeadersAreIndependentFromTheOriginal() {
        DefaultStompHeaders original = new DefaultStompHeaders();
        original.add(StompHeaders.DESTINATION, "/queue/orders");
        original.add(StompHeaders.RECEIPT, "receipt-1");

        DefaultStompHeaders copy = original.copy();
        copy.set(StompHeaders.DESTINATION, "/queue/audit");
        copy.add(StompHeaders.RECEIPT, "receipt-2");

        assertThat(original.getAsString(StompHeaders.DESTINATION)).isEqualTo("/queue/orders");
        assertThat(original.getAllAsString(StompHeaders.RECEIPT)).containsExactly("receipt-1");
        assertThat(copy.getAsString(StompHeaders.DESTINATION)).isEqualTo("/queue/audit");
        assertThat(copy.getAllAsString(StompHeaders.RECEIPT)).containsExactly("receipt-1", "receipt-2");
    }

    @Test
    void fullFrameEncoderWritesCommandHeadersContentAndTerminator() {
        EmbeddedChannel channel = new EmbeddedChannel(new StompSubframeEncoder());
        ByteBuf content = Unpooled.copiedBuffer("hello broker", UTF_8);
        DefaultStompFrame frame = new DefaultStompFrame(StompCommand.SEND, content);
        frame.headers().set(StompHeaders.DESTINATION, "/queue/orders");
        frame.headers().set(StompHeaders.CONTENT_TYPE, "text/plain");
        frame.headers().setLong(StompHeaders.CONTENT_LENGTH, content.readableBytes());

        try {
            assertThat(channel.writeOutbound(frame)).isTrue();
            ByteBuf encoded = readAllOutboundBytes(channel);
            try {
                assertThat(encoded.toString(UTF_8)).isEqualTo(
                        "SEND\n"
                                + "destination:/queue/orders\n"
                                + "content-type:text/plain\n"
                                + "content-length:12\n"
                                + "\n"
                                + "hello broker\0");
            } finally {
                encoded.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void subframeEncoderWritesHeaderAndContentFramesSeparately() {
        EmbeddedChannel channel = new EmbeddedChannel(new StompSubframeEncoder());
        DefaultStompHeadersSubframe headers = new DefaultStompHeadersSubframe(StompCommand.SUBSCRIBE);
        headers.headers().set(StompHeaders.ID, "subscription-1");
        headers.headers().set(StompHeaders.DESTINATION, "/topic/events");
        DefaultStompContentSubframe bodyChunk = new DefaultStompContentSubframe(
                Unpooled.copiedBuffer("chunk", UTF_8));
        LastStompContentSubframe lastChunk = new DefaultLastStompContentSubframe(
                Unpooled.copiedBuffer("last", UTF_8));

        try {
            assertThat(channel.writeOutbound(headers, bodyChunk, lastChunk)).isTrue();
            ByteBuf encoded = readAllOutboundBytes(channel);
            try {
                assertThat(encoded.toString(UTF_8)).isEqualTo(
                        "SUBSCRIBE\n"
                                + "id:subscription-1\n"
                                + "destination:/topic/events\n"
                                + "\n"
                                + "chunklast\0");
            } finally {
                encoded.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decoderSkipsLeadingControlCharactersAndEmitsChunkedContent() {
        EmbeddedChannel channel = new EmbeddedChannel(new StompSubframeDecoder(256, 4));
        ByteBuf encoded = Unpooled.copiedBuffer(
                "\r\nSEND\n"
                        + "destination:/queue/orders\n"
                        + "content-length:11\n"
                        + "\n"
                        + "hello world\0",
                UTF_8);

        try {
            assertThat(channel.writeInbound(encoded)).isTrue();
            StompHeadersSubframe headers = channel.readInbound();
            StompContentSubframe firstChunk = channel.readInbound();
            StompContentSubframe secondChunk = channel.readInbound();
            LastStompContentSubframe lastChunk = channel.readInbound();
            try {
                assertThat(headers.command()).isEqualTo(StompCommand.SEND);
                assertThat(headers.headers().getAsString(StompHeaders.DESTINATION)).isEqualTo("/queue/orders");
                assertThat(headers.headers().getLong(StompHeaders.CONTENT_LENGTH)).isEqualTo(11L);
                assertThat(headers.decoderResult()).isEqualTo(DecoderResult.SUCCESS);
                assertThat(firstChunk.content().toString(UTF_8)).isEqualTo("hell");
                assertThat(secondChunk.content().toString(UTF_8)).isEqualTo("o wo");
                assertThat(lastChunk.content().toString(UTF_8)).isEqualTo("rld");
                assertThat((Object) channel.readInbound()).isNull();
            } finally {
                ReferenceCountUtil.release(headers);
                firstChunk.release();
                secondChunk.release();
                lastChunk.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decoderAggregatesContentLengthFrameIntoFullStompFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new StompSubframeDecoder(256, 3),
                new StompSubframeAggregator(64));
        ByteBuf encoded = Unpooled.copiedBuffer(
                "MESSAGE\n"
                        + "subscription:sub-1\n"
                        + "message-id:msg-42\n"
                        + "destination:/topic/events\n"
                        + "content-length:11\n"
                        + "\n"
                        + "hello world\0",
                UTF_8);

        try {
            assertThat(channel.writeInbound(encoded)).isTrue();
            StompFrame frame = channel.readInbound();
            try {
                assertThat(frame.command()).isEqualTo(StompCommand.MESSAGE);
                assertThat(frame.headers().getAsString(StompHeaders.SUBSCRIPTION)).isEqualTo("sub-1");
                assertThat(frame.headers().getAsString(StompHeaders.MESSAGE_ID)).isEqualTo("msg-42");
                assertThat(frame.headers().getAsString(StompHeaders.DESTINATION)).isEqualTo("/topic/events");
                assertThat(frame.content().toString(UTF_8)).isEqualTo("hello world");
                assertThat(frame.decoderResult()).isEqualTo(DecoderResult.SUCCESS);
                assertThat((Object) channel.readInbound()).isNull();
            } finally {
                frame.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decoderPreservesEmbeddedNulBytesWhenContentLengthIsPresent() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new StompSubframeDecoder(256, 2),
                new StompSubframeAggregator(64));
        byte[] payload = new byte[] {'A', 0, 'B'};
        ByteBuf encoded = Unpooled.buffer();
        encoded.writeCharSequence(
                "MESSAGE\n"
                        + "subscription:sub-1\n"
                        + "message-id:msg-binary\n"
                        + "destination:/queue/binary\n"
                        + "content-length:" + payload.length + "\n"
                        + "\n",
                UTF_8);
        encoded.writeBytes(payload);
        encoded.writeByte(0);

        try {
            assertThat(channel.writeInbound(encoded)).isTrue();
            StompFrame frame = channel.readInbound();
            try {
                assertThat(frame.command()).isEqualTo(StompCommand.MESSAGE);
                assertThat(frame.headers().getAsString(StompHeaders.MESSAGE_ID)).isEqualTo("msg-binary");
                assertThat(frame.content().readableBytes()).isEqualTo(payload.length);
                assertThat(frame.content().getByte(0)).isEqualTo((byte) 'A');
                assertThat(frame.content().getByte(1)).isZero();
                assertThat(frame.content().getByte(2)).isEqualTo((byte) 'B');
                assertThat(frame.decoderResult()).isEqualTo(DecoderResult.SUCCESS);
                assertThat((Object) channel.readInbound()).isNull();
            } finally {
                frame.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decoderReadsNulTerminatedFrameWithoutContentLength() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new StompSubframeDecoder(256, 16),
                new StompSubframeAggregator(64));
        ByteBuf encoded = Unpooled.copiedBuffer(
                "ERROR\n"
                        + "message:malformed command\n"
                        + "\n"
                        + "explanation body\0",
                UTF_8);

        try {
            assertThat(channel.writeInbound(encoded)).isTrue();
            StompFrame frame = channel.readInbound();
            try {
                assertThat(frame.command()).isEqualTo(StompCommand.ERROR);
                assertThat(frame.headers().getAsString(StompHeaders.MESSAGE)).isEqualTo("malformed command");
                assertThat(frame.content().toString(UTF_8)).isEqualTo("explanation body");
            } finally {
                frame.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decoderFinalizesZeroContentLengthFrameAndContinuesWithNextFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new StompSubframeDecoder(256, 16));
        ByteBuf encoded = Unpooled.copiedBuffer(
                "RECEIPT\n"
                        + "receipt-id:receipt-1\n"
                        + "content-length:0\n"
                        + "\n"
                        + "\0"
                        + "\n"
                        + "CONNECTED\n"
                        + "version:1.2\n"
                        + "session:session-1\n"
                        + "\n"
                        + "\0",
                UTF_8);

        try {
            assertThat(channel.writeInbound(encoded)).isTrue();
            StompHeadersSubframe firstHeaders = channel.readInbound();
            LastStompContentSubframe firstLastContent = channel.readInbound();
            StompHeadersSubframe secondHeaders = channel.readInbound();
            LastStompContentSubframe secondLastContent = channel.readInbound();
            try {
                assertThat(firstHeaders.command()).isEqualTo(StompCommand.RECEIPT);
                assertThat(firstHeaders.headers().getAsString(StompHeaders.RECEIPT_ID)).isEqualTo("receipt-1");
                assertThat(firstHeaders.headers().getLong(StompHeaders.CONTENT_LENGTH)).isZero();
                assertThat(firstHeaders.decoderResult()).isEqualTo(DecoderResult.SUCCESS);
                assertThat(firstLastContent.content().readableBytes()).isZero();
                assertThat(firstLastContent.decoderResult()).isEqualTo(DecoderResult.SUCCESS);

                assertThat(secondHeaders.command()).isEqualTo(StompCommand.CONNECTED);
                assertThat(secondHeaders.headers().getAsString(StompHeaders.VERSION)).isEqualTo("1.2");
                assertThat(secondHeaders.headers().getAsString(StompHeaders.SESSION)).isEqualTo("session-1");
                assertThat(secondHeaders.decoderResult()).isEqualTo(DecoderResult.SUCCESS);
                assertThat(secondLastContent.content().readableBytes()).isZero();
                assertThat(secondLastContent.decoderResult()).isEqualTo(DecoderResult.SUCCESS);
                assertThat((Object) channel.readInbound()).isNull();
            } finally {
                ReferenceCountUtil.release(firstHeaders);
                ReferenceCountUtil.release(firstLastContent);
                ReferenceCountUtil.release(secondHeaders);
                ReferenceCountUtil.release(secondLastContent);
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decoderReportsInvalidHeadersWhenValidationIsEnabled() {
        EmbeddedChannel channel = new EmbeddedChannel(new StompSubframeDecoder(256, 16, true));
        ByteBuf encoded = Unpooled.copiedBuffer(
                "SEND\n"
                        + "destination:/queue/orders:extra-colon\n"
                        + "\n"
                        + "\0",
                UTF_8);

        try {
            assertThat(channel.writeInbound(encoded)).isTrue();
            StompHeadersSubframe headers = channel.readInbound();
            try {
                assertThat(headers.command()).isEqualTo(StompCommand.SEND);
                assertThat(headers.decoderResult().isFailure()).isTrue();
                assertThat(headers.decoderResult().cause())
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("prohibited character ':'");
                assertThat(headers.headers().isEmpty()).isTrue();
            } finally {
                ReferenceCountUtil.release(headers);
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void frameCopyDuplicateReplaceAndReferenceCountingPreserveHeaders() {
        DefaultStompFrame frame = new DefaultStompFrame(
                StompCommand.MESSAGE,
                Unpooled.copiedBuffer("payload", UTF_8));
        frame.headers().set(StompHeaders.SUBSCRIPTION, "sub-1");
        frame.headers().set(StompHeaders.MESSAGE_ID, "msg-1");

        StompFrame copy = frame.copy();
        StompFrame duplicate = frame.duplicate();
        StompFrame replacement = frame.replace(Unpooled.copiedBuffer("replacement", UTF_8));
        try {
            frame.content().setByte(0, 'P');

            assertThat(copy.command()).isEqualTo(StompCommand.MESSAGE);
            assertThat(copy.headers().getAsString(StompHeaders.SUBSCRIPTION)).isEqualTo("sub-1");
            assertThat(copy.content().toString(UTF_8)).isEqualTo("payload");
            assertThat(duplicate.content().toString(UTF_8)).isEqualTo("Payload");
            assertThat(replacement.command()).isEqualTo(StompCommand.MESSAGE);
            assertThat(replacement.headers().getAsString(StompHeaders.MESSAGE_ID)).isEqualTo("msg-1");
            assertThat(replacement.content().toString(UTF_8)).isEqualTo("replacement");
            assertThat(frame.retain()).isSameAs(frame);
            assertThat(frame.refCnt()).isEqualTo(2);
        } finally {
            copy.release();
            duplicate.release();
            replacement.release();
            frame.release();
        }
    }

    @Test
    void lastContentEmptySingletonAndReplacementBehaveAsByteBufHolders() {
        LastStompContentSubframe empty = LastStompContentSubframe.EMPTY_LAST_CONTENT;

        assertThat(empty.content().readableBytes()).isZero();
        assertThat(empty.copy()).isSameAs(empty);
        assertThat(empty.duplicate()).isSameAs(empty);
        assertThat(empty.retainedDuplicate()).isSameAs(empty);
        assertThat(empty.release()).isFalse();
        assertThat(empty.refCnt()).isEqualTo(1);
        assertThatThrownBy(() -> empty.setDecoderResult(DecoderResult.failure(new IllegalStateException("boom"))))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("read only");

        LastStompContentSubframe replacement = empty.replace(Unpooled.copiedBuffer("tail", UTF_8));
        try {
            assertThat(replacement).isInstanceOf(DefaultLastStompContentSubframe.class);
            assertThat(replacement.content().toString(UTF_8)).isEqualTo("tail");
            assertThat(replacement.decoderResult()).isEqualTo(DecoderResult.SUCCESS);
        } finally {
            replacement.release();
        }
    }

    @Test
    void stompCommandEnumExposesProtocolCommands() {
        List<StompCommand> commands = Arrays.asList(StompCommand.values());

        assertThat(commands).contains(
                StompCommand.STOMP,
                StompCommand.CONNECT,
                StompCommand.CONNECTED,
                StompCommand.SEND,
                StompCommand.SUBSCRIBE,
                StompCommand.UNSUBSCRIBE,
                StompCommand.ACK,
                StompCommand.NACK,
                StompCommand.BEGIN,
                StompCommand.ABORT,
                StompCommand.COMMIT,
                StompCommand.DISCONNECT,
                StompCommand.MESSAGE,
                StompCommand.RECEIPT,
                StompCommand.ERROR,
                StompCommand.UNKNOWN);
        assertThat(StompCommand.valueOf("SEND")).isEqualTo(StompCommand.SEND);
    }

    private static ByteBuf readAllOutboundBytes(EmbeddedChannel channel) {
        ByteBuf aggregate = Unpooled.buffer();
        ByteBuf next = channel.readOutbound();
        while (next != null) {
            try {
                aggregate.writeBytes(next);
            } finally {
                next.release();
            }
            next = channel.readOutbound();
        }
        return aggregate;
    }
}
