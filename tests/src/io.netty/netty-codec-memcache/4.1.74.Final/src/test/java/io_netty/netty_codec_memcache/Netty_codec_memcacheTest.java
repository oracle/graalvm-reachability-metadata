/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_memcache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.memcache.DefaultLastMemcacheContent;
import io.netty.handler.codec.memcache.DefaultMemcacheContent;
import io.netty.handler.codec.memcache.LastMemcacheContent;
import io.netty.handler.codec.memcache.MemcacheContent;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheClientCodec;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheObjectAggregator;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheOpcodes;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheRequest;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheRequestDecoder;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheRequestEncoder;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheResponse;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheResponseDecoder;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheResponseStatus;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheServerCodec;
import io.netty.handler.codec.memcache.binary.DefaultBinaryMemcacheRequest;
import io.netty.handler.codec.memcache.binary.DefaultBinaryMemcacheResponse;
import io.netty.handler.codec.memcache.binary.DefaultFullBinaryMemcacheRequest;
import io.netty.handler.codec.memcache.binary.DefaultFullBinaryMemcacheResponse;
import io.netty.handler.codec.memcache.binary.FullBinaryMemcacheRequest;
import io.netty.handler.codec.memcache.binary.FullBinaryMemcacheResponse;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

public class Netty_codec_memcacheTest {
    private static final byte RESPONSE_MAGIC = (byte) 0x81;
    private static final int HEADER_SIZE = 24;

    @Test
    void encodesFullBinarySetRequestHeaderKeyExtrasAndContent() {
        EmbeddedChannel channel = new EmbeddedChannel(new BinaryMemcacheRequestEncoder());
        ByteBuf extras = Unpooled.buffer(8).writeInt(0x01020304).writeInt(60);
        FullBinaryMemcacheRequest request = new DefaultFullBinaryMemcacheRequest(
                ascii("cache-key"), extras, ascii("value"));
        request.setOpcode(BinaryMemcacheOpcodes.SET);
        request.setReserved((short) 17);
        request.setOpaque(0x11223344);
        request.setCas(0x0102030405060708L);
        request.setTotalBodyLength(request.extrasLength() + request.keyLength() + request.content().readableBytes());

        assertThat(channel.writeOutbound(request)).isTrue();
        ByteBuf encoded = collectOutboundBytes(channel);
        try {
            assertThat(encoded.readableBytes()).isEqualTo(HEADER_SIZE + 8 + "cache-key".length() + "value".length());
            assertThat((int) encoded.readUnsignedByte())
                    .isEqualTo(DefaultBinaryMemcacheRequest.REQUEST_MAGIC_BYTE & 0xff);
            assertThat(encoded.readByte()).isEqualTo(BinaryMemcacheOpcodes.SET);
            assertThat(encoded.readUnsignedShort()).isEqualTo("cache-key".length());
            assertThat((int) encoded.readUnsignedByte()).isEqualTo(8);
            assertThat((int) encoded.readUnsignedByte()).isZero();
            assertThat(encoded.readUnsignedShort()).isEqualTo(17);
            assertThat(encoded.readInt()).isEqualTo(8 + "cache-key".length() + "value".length());
            assertThat(encoded.readInt()).isEqualTo(0x11223344);
            assertThat(encoded.readLong()).isEqualTo(0x0102030405060708L);
            assertThat(encoded.readInt()).isEqualTo(0x01020304);
            assertThat(encoded.readInt()).isEqualTo(60);
            assertThat(readAscii(encoded, "cache-key".length())).isEqualTo("cache-key");
            assertThat(readAscii(encoded, "value".length())).isEqualTo("value");
            assertThat(encoded.isReadable()).isFalse();
        } finally {
            encoded.release();
            assertThat(channel.finishAndReleaseAll()).isFalse();
        }
    }

    @Test
    void decodesFragmentedBinaryResponseIntoMessageAndContentChunks() {
        EmbeddedChannel channel = new EmbeddedChannel(new BinaryMemcacheResponseDecoder(3));
        ByteBuf responseBytes = binaryResponse(BinaryMemcacheOpcodes.GET, BinaryMemcacheResponseStatus.SUCCESS,
                0x0a0b0c0d, 0x0102030405060708L, flagsExtras(0xdeadbeef), ascii("alpha"), ascii("bravo"));
        ByteBuf firstFragment = responseBytes.copy(0, 10);
        ByteBuf secondFragment = responseBytes.copy(10, responseBytes.readableBytes() - 10);
        responseBytes.release();

        assertThat(channel.writeInbound(firstFragment)).isFalse();
        assertThat(channel.writeInbound(secondFragment)).isTrue();

        BinaryMemcacheResponse response = channel.readInbound();
        try {
            assertThat(response.magic()).isEqualTo(RESPONSE_MAGIC);
            assertThat(response.opcode()).isEqualTo(BinaryMemcacheOpcodes.GET);
            assertThat(response.status()).isEqualTo(BinaryMemcacheResponseStatus.SUCCESS);
            assertThat(response.opaque()).isEqualTo(0x0a0b0c0d);
            assertThat(response.cas()).isEqualTo(0x0102030405060708L);
            assertThat(response.key().toString(CharsetUtil.UTF_8)).isEqualTo("alpha");
            assertThat(response.extras().getInt(response.extras().readerIndex())).isEqualTo(0xdeadbeef);
        } finally {
            response.release();
        }

        StringBuilder content = new StringBuilder();
        int chunks = 0;
        boolean sawLastContent = false;
        Object inbound;
        while ((inbound = channel.readInbound()) != null) {
            assertThat(inbound).isInstanceOf(MemcacheContent.class);
            MemcacheContent memcacheContent = (MemcacheContent) inbound;
            content.append(memcacheContent.content().toString(CharsetUtil.UTF_8));
            chunks++;
            sawLastContent |= inbound instanceof LastMemcacheContent;
            ReferenceCountUtil.release(inbound);
        }

        assertThat(content).hasToString("bravo");
        assertThat(chunks).isGreaterThanOrEqualTo(2);
        assertThat(sawLastContent).isTrue();
        assertThat(channel.finishAndReleaseAll()).isFalse();
    }

    @Test
    void aggregatesDecodedBinaryResponseIntoFullMessage() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new BinaryMemcacheResponseDecoder(4),
                new BinaryMemcacheObjectAggregator(1024));
        ByteBuf responseBytes = binaryResponse(BinaryMemcacheOpcodes.GETK, BinaryMemcacheResponseStatus.SUCCESS,
                0x12345678, 99L, flagsExtras(0x0000002a), ascii("aggregated-key"), ascii("aggregated-value"));

        assertThat(channel.writeInbound(responseBytes)).isTrue();
        FullBinaryMemcacheResponse response = channel.readInbound();
        try {
            assertThat(response.magic()).isEqualTo(RESPONSE_MAGIC);
            assertThat(response.opcode()).isEqualTo(BinaryMemcacheOpcodes.GETK);
            assertThat(response.status()).isEqualTo(BinaryMemcacheResponseStatus.SUCCESS);
            assertThat(response.opaque()).isEqualTo(0x12345678);
            assertThat(response.cas()).isEqualTo(99L);
            assertThat(response.extras().getInt(response.extras().readerIndex())).isEqualTo(42);
            assertThat(response.key().toString(CharsetUtil.UTF_8)).isEqualTo("aggregated-key");
            assertThat(response.content().toString(CharsetUtil.UTF_8)).isEqualTo("aggregated-value");
            assertThat(response.totalBodyLength())
                    .isEqualTo(response.extrasLength() + response.keyLength() + response.content().readableBytes());
        } finally {
            response.release();
            assertThat(channel.finishAndReleaseAll()).isFalse();
        }
    }

    @Test
    void aggregatesFragmentedSetRequestIntoFullMessage() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new BinaryMemcacheRequestDecoder(4),
                new BinaryMemcacheObjectAggregator(1024));
        ByteBuf requestBytes = binaryRequest(BinaryMemcacheOpcodes.SET, (short) 0x2211,
                0x55667788, 0x0102030405060708L, setExtras(0x01020304, 300),
                ascii("aggregate-request-key"), ascii("request-value"));
        ByteBuf firstFragment = requestBytes.copy(0, 13);
        ByteBuf secondFragment = requestBytes.copy(13, requestBytes.readableBytes() - 13);
        requestBytes.release();

        assertThat(channel.writeInbound(firstFragment)).isFalse();
        assertThat(channel.writeInbound(secondFragment)).isTrue();
        FullBinaryMemcacheRequest request = channel.readInbound();
        try {
            assertThat(request.magic()).isEqualTo(DefaultBinaryMemcacheRequest.REQUEST_MAGIC_BYTE);
            assertThat(request.opcode()).isEqualTo(BinaryMemcacheOpcodes.SET);
            assertThat(request.reserved()).isEqualTo((short) 0x2211);
            assertThat(request.opaque()).isEqualTo(0x55667788);
            assertThat(request.cas()).isEqualTo(0x0102030405060708L);
            assertThat((int) request.extrasLength()).isEqualTo(8);
            assertThat(request.extras().getInt(request.extras().readerIndex())).isEqualTo(0x01020304);
            assertThat(request.extras().getInt(request.extras().readerIndex() + 4)).isEqualTo(300);
            assertThat(request.key().toString(CharsetUtil.UTF_8)).isEqualTo("aggregate-request-key");
            assertThat(request.content().toString(CharsetUtil.UTF_8)).isEqualTo("request-value");
            assertThat(request.totalBodyLength())
                    .isEqualTo(request.extrasLength() + request.keyLength() + request.content().readableBytes());
        } finally {
            request.release();
            assertThat(channel.finishAndReleaseAll()).isFalse();
        }
    }

    @Test
    void clientCodecEncodesRequestsAndDecodesResponses() {
        EmbeddedChannel channel = new EmbeddedChannel(new BinaryMemcacheClientCodec(128, true));
        DefaultBinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest(ascii("client-key"));
        request.setOpcode(BinaryMemcacheOpcodes.GET);
        request.setOpaque(7);
        request.setTotalBodyLength(request.keyLength());

        assertThat(channel.writeOutbound(request)).isTrue();
        ByteBuf encodedRequest = collectOutboundBytes(channel);
        try {
            assertThat((int) encodedRequest.readUnsignedByte())
                    .isEqualTo(DefaultBinaryMemcacheRequest.REQUEST_MAGIC_BYTE & 0xff);
            assertThat(encodedRequest.readByte()).isEqualTo(BinaryMemcacheOpcodes.GET);
            assertThat(encodedRequest.readUnsignedShort()).isEqualTo("client-key".length());
            encodedRequest.skipBytes(4);
            assertThat(encodedRequest.readInt()).isEqualTo("client-key".length());
            assertThat(encodedRequest.readInt()).isEqualTo(7);
            encodedRequest.skipBytes(8);
            assertThat(readAscii(encodedRequest, "client-key".length())).isEqualTo("client-key");
        } finally {
            encodedRequest.release();
        }

        ByteBuf responseBytes = binaryResponse(BinaryMemcacheOpcodes.GET, BinaryMemcacheResponseStatus.KEY_ENOENT,
                7, 0L, Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER, ascii("missing"));
        assertThat(channel.writeInbound(responseBytes)).isTrue();
        BinaryMemcacheResponse response = channel.readInbound();
        try {
            assertThat(response.status()).isEqualTo(BinaryMemcacheResponseStatus.KEY_ENOENT);
            assertThat(response.opaque()).isEqualTo(7);
        } finally {
            response.release();
        }
        LastMemcacheContent content = readUntilLastContent(channel);
        try {
            assertThat(content.content().toString(CharsetUtil.UTF_8)).isEqualTo("missing");
        } finally {
            content.release();
            assertThat(channel.finishAndReleaseAll()).isFalse();
        }
    }

    @Test
    void clientCodecReportsMissingFullResponseWhenChannelCloses() {
        EmbeddedChannel channel = new EmbeddedChannel(new BinaryMemcacheClientCodec(128, true));
        FullBinaryMemcacheRequest request = new DefaultFullBinaryMemcacheRequest(
                ascii("tracked-key"), Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER);
        request.setOpcode(BinaryMemcacheOpcodes.GET);
        request.setTotalBodyLength(request.keyLength());

        assertThat(channel.writeOutbound(request)).isTrue();
        ByteBuf encodedRequest = collectOutboundBytes(channel);
        encodedRequest.release();

        channel.close();
        assertThatExceptionOfType(PrematureChannelClosureException.class)
                .isThrownBy(channel::checkException)
                .withMessageContaining("1 missing response");
        assertThat(channel.finishAndReleaseAll()).isFalse();
    }

    @Test
    void serverCodecDecodesRequestsAndEncodesResponses() {
        EmbeddedChannel channel = new EmbeddedChannel(new BinaryMemcacheServerCodec(128));
        ByteBuf requestBytes = binaryRequest(BinaryMemcacheOpcodes.GET, (short) 12,
                0x01010101, 0L, Unpooled.EMPTY_BUFFER, ascii("server-key"), Unpooled.EMPTY_BUFFER);

        assertThat(channel.writeInbound(requestBytes)).isTrue();
        BinaryMemcacheRequest request = channel.readInbound();
        try {
            assertThat(request.magic()).isEqualTo(DefaultBinaryMemcacheRequest.REQUEST_MAGIC_BYTE);
            assertThat(request.opcode()).isEqualTo(BinaryMemcacheOpcodes.GET);
            assertThat(request.reserved()).isEqualTo((short) 12);
            assertThat(request.opaque()).isEqualTo(0x01010101);
            assertThat(request.key().toString(CharsetUtil.UTF_8)).isEqualTo("server-key");
        } finally {
            request.release();
        }
        LastMemcacheContent endOfRequest = channel.readInbound();
        try {
            assertThat(endOfRequest.content().isReadable()).isFalse();
        } finally {
            endOfRequest.release();
        }
        Object nextInbound = channel.readInbound();
        assertThat(nextInbound).isNull();

        FullBinaryMemcacheResponse response = new DefaultFullBinaryMemcacheResponse(Unpooled.EMPTY_BUFFER,
                flagsExtras(0x01000000), ascii("stored"));
        response.setOpcode(BinaryMemcacheOpcodes.GET);
        response.setStatus(BinaryMemcacheResponseStatus.SUCCESS);
        response.setOpaque(0x01010101);
        response.setTotalBodyLength(
                response.extrasLength() + response.keyLength() + response.content().readableBytes());
        assertThat(channel.writeOutbound(response)).isTrue();
        ByteBuf encodedResponse = collectOutboundBytes(channel);
        try {
            assertThat((int) encodedResponse.readUnsignedByte())
                    .isEqualTo(DefaultBinaryMemcacheResponse.RESPONSE_MAGIC_BYTE & 0xff);
            assertThat(encodedResponse.readByte()).isEqualTo(BinaryMemcacheOpcodes.GET);
            assertThat(encodedResponse.readUnsignedShort()).isZero();
            assertThat((int) encodedResponse.readUnsignedByte()).isEqualTo(4);
            assertThat((int) encodedResponse.readUnsignedByte()).isZero();
            assertThat(encodedResponse.readUnsignedShort()).isEqualTo(BinaryMemcacheResponseStatus.SUCCESS);
            assertThat(encodedResponse.readInt()).isEqualTo(4 + "stored".length());
            assertThat(encodedResponse.readInt()).isEqualTo(0x01010101);
            encodedResponse.skipBytes(8);
            assertThat(encodedResponse.readInt()).isEqualTo(0x01000000);
            assertThat(readAscii(encodedResponse, "stored".length())).isEqualTo("stored");
        } finally {
            encodedResponse.release();
            assertThat(channel.finishAndReleaseAll()).isFalse();
        }
    }

    @Test
    void contentAndFullMessageHoldersCopyDuplicateRetainAndReplaceBuffers() {
        MemcacheContent content = new DefaultMemcacheContent(ascii("payload"));
        MemcacheContent copy = content.copy();
        MemcacheContent retainedDuplicate = content.retainedDuplicate();
        MemcacheContent replacement = content.replace(ascii("replacement"));
        try {
            assertThat(content.content().toString(CharsetUtil.UTF_8)).isEqualTo("payload");
            assertThat(copy.content().toString(CharsetUtil.UTF_8)).isEqualTo("payload");
            assertThat(retainedDuplicate.content().toString(CharsetUtil.UTF_8)).isEqualTo("payload");
            assertThat(replacement.content().toString(CharsetUtil.UTF_8)).isEqualTo("replacement");
            assertThat(content.refCnt()).isEqualTo(1);
        } finally {
            replacement.release();
            retainedDuplicate.release();
            copy.release();
            content.release();
        }

        LastMemcacheContent last = new DefaultLastMemcacheContent(ascii("last"));
        LastMemcacheContent lastCopy = last.copy();
        LastMemcacheContent lastReplacement = last.replace(ascii("done"));
        try {
            assertThat(last.content().toString(CharsetUtil.UTF_8)).isEqualTo("last");
            assertThat(lastCopy.content().toString(CharsetUtil.UTF_8)).isEqualTo("last");
            assertThat(lastReplacement.content().toString(CharsetUtil.UTF_8)).isEqualTo("done");
        } finally {
            lastReplacement.release();
            lastCopy.release();
            last.release();
        }

        FullBinaryMemcacheResponse response = new DefaultFullBinaryMemcacheResponse(ascii("full-key"),
                flagsExtras(1), ascii("full-value"));
        response.setOpcode(BinaryMemcacheOpcodes.GETK);
        response.setStatus(BinaryMemcacheResponseStatus.SUCCESS);
        response.setOpaque(44);
        FullBinaryMemcacheResponse fullCopy = response.copy();
        FullBinaryMemcacheResponse fullReplacement = response.replace(ascii("new-value"));
        try {
            assertThat(fullCopy.key().toString(CharsetUtil.UTF_8)).isEqualTo("full-key");
            assertThat(fullCopy.extras().getInt(fullCopy.extras().readerIndex())).isEqualTo(1);
            assertThat(fullCopy.content().toString(CharsetUtil.UTF_8)).isEqualTo("full-value");
            assertThat(fullReplacement.opcode()).isEqualTo(BinaryMemcacheOpcodes.GETK);
            assertThat(fullReplacement.status()).isEqualTo(BinaryMemcacheResponseStatus.SUCCESS);
            assertThat(fullReplacement.opaque()).isEqualTo(44);
            assertThat(fullReplacement.content().toString(CharsetUtil.UTF_8)).isEqualTo("new-value");
        } finally {
            fullReplacement.release();
            fullCopy.release();
            response.release();
        }
    }

    private static ByteBuf ascii(String value) {
        return Unpooled.copiedBuffer(value, CharsetUtil.UTF_8);
    }

    private static ByteBuf flagsExtras(int flags) {
        return Unpooled.buffer(4).writeInt(flags);
    }

    private static ByteBuf setExtras(int flags, int expiration) {
        return Unpooled.buffer(8).writeInt(flags).writeInt(expiration);
    }

    private static String readAscii(ByteBuf buffer, int length) {
        return buffer.readCharSequence(length, CharsetUtil.UTF_8).toString();
    }

    private static ByteBuf collectOutboundBytes(EmbeddedChannel channel) {
        ByteBuf combined = Unpooled.buffer();
        Object outbound;
        while ((outbound = channel.readOutbound()) != null) {
            assertThat(outbound).isInstanceOf(ByteBuf.class);
            ByteBuf part = (ByteBuf) outbound;
            try {
                combined.writeBytes(part);
            } finally {
                part.release();
            }
        }
        return combined;
    }

    private static LastMemcacheContent readUntilLastContent(EmbeddedChannel channel) {
        LastMemcacheContent lastContent = null;
        Object inbound;
        while ((inbound = channel.readInbound()) != null) {
            assertThat(inbound).isInstanceOf(MemcacheContent.class);
            if (inbound instanceof LastMemcacheContent) {
                assertThat(lastContent).isNull();
                lastContent = (LastMemcacheContent) inbound;
            } else {
                ReferenceCountUtil.release(inbound);
            }
        }
        assertThat(lastContent).isNotNull();
        return lastContent;
    }

    private static ByteBuf binaryRequest(byte opcode, short reserved, int opaque, long cas,
            ByteBuf extras, ByteBuf key, ByteBuf content) {
        return binaryMessage(
                DefaultBinaryMemcacheRequest.REQUEST_MAGIC_BYTE, opcode, reserved, opaque, cas, extras, key, content);
    }

    private static ByteBuf binaryResponse(byte opcode, short status, int opaque, long cas,
            ByteBuf extras, ByteBuf key, ByteBuf content) {
        return binaryMessage(
                DefaultBinaryMemcacheResponse.RESPONSE_MAGIC_BYTE, opcode, status, opaque, cas, extras, key, content);
    }

    private static ByteBuf binaryMessage(byte magic, byte opcode, short statusOrReserved, int opaque, long cas,
            ByteBuf extras, ByteBuf key, ByteBuf content) {
        int extrasLength = extras.readableBytes();
        int keyLength = key.readableBytes();
        int contentLength = content.readableBytes();
        ByteBuf message = Unpooled.buffer(HEADER_SIZE + extrasLength + keyLength + contentLength);
        message.writeByte(magic);
        message.writeByte(opcode);
        message.writeShort(keyLength);
        message.writeByte(extrasLength);
        message.writeByte(0);
        message.writeShort(statusOrReserved);
        message.writeInt(extrasLength + keyLength + contentLength);
        message.writeInt(opaque);
        message.writeLong(cas);
        message.writeBytes(extras, extras.readerIndex(), extrasLength);
        message.writeBytes(key, key.readerIndex(), keyLength);
        message.writeBytes(content, content.readerIndex(), contentLength);
        extras.release();
        key.release();
        content.release();
        return message;
    }
}
