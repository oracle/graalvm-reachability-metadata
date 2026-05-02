/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_haproxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ProtocolDetectionResult;
import io.netty.handler.codec.ProtocolDetectionState;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyMessageEncoder;
import io.netty.handler.codec.haproxy.HAProxyProtocolException;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;
import io.netty.handler.codec.haproxy.HAProxySSLTLV;
import io.netty.handler.codec.haproxy.HAProxyTLV;
import io.netty.util.CharsetUtil;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class Netty_codec_haproxyTest {
    private static final byte[] BINARY_PREFIX = new byte[] {
        0x0d, 0x0a, 0x0d, 0x0a, 0x00, 0x0d, 0x0a, 0x51, 0x55, 0x49, 0x54, 0x0a
    };

    @Test
    void detectsProtocolVersionsWithoutMovingReaderIndex() {
        ByteBuf partial = Unpooled.wrappedBuffer(new byte[] {0x0d, 0x0a});
        ByteBuf binary = Unpooled.wrappedBuffer(BINARY_PREFIX);
        ByteBuf text = Unpooled.copiedBuffer("PROXY TCP4 192.0.2.10", CharsetUtil.US_ASCII);
        ByteBuf invalid = Unpooled.copiedBuffer("GET / HTTP/1.1", CharsetUtil.US_ASCII);
        try {
            assertDetection(partial, ProtocolDetectionState.NEEDS_MORE_DATA, null);
            assertDetection(binary, ProtocolDetectionState.DETECTED, HAProxyProtocolVersion.V2);
            assertDetection(text, ProtocolDetectionState.DETECTED, HAProxyProtocolVersion.V1);
            assertDetection(invalid, ProtocolDetectionState.INVALID, null);
        } finally {
            partial.release();
            binary.release();
            text.release();
            invalid.release();
        }
    }

    @Test
    void decodesTextProtocolTcp4Header() {
        EmbeddedChannel channel = new EmbeddedChannel(new HAProxyMessageDecoder());

        assertThat(channel.writeInbound(
                Unpooled.copiedBuffer("PROXY TCP4 203.0.113.1 198.51.100.10 56324 443\r\n", CharsetUtil.US_ASCII)))
                .isTrue();
        HAProxyMessage message = channel.readInbound();
        try {
            assertThat(message.protocolVersion()).isEqualTo(HAProxyProtocolVersion.V1);
            assertThat(message.command()).isEqualTo(HAProxyCommand.PROXY);
            assertThat(message.proxiedProtocol()).isEqualTo(HAProxyProxiedProtocol.TCP4);
            assertThat(message.sourceAddress()).isEqualTo("203.0.113.1");
            assertThat(message.destinationAddress()).isEqualTo("198.51.100.10");
            assertThat(message.sourcePort()).isEqualTo(56324);
            assertThat(message.destinationPort()).isEqualTo(443);
            assertThat(message.tlvs()).isEmpty();
        } finally {
            message.release();
            assertThat(channel.finish()).isFalse();
        }
    }

    @Test
    void encodesTextProtocolTcp6Header() {
        EmbeddedChannel channel = new EmbeddedChannel(HAProxyMessageEncoder.INSTANCE);
        HAProxyMessage message = new HAProxyMessage(
                HAProxyProtocolVersion.V1,
                HAProxyCommand.PROXY,
                HAProxyProxiedProtocol.TCP6,
                "2001:db8::1",
                "2001:db8::2",
                12345,
                8443);

        assertThat(channel.writeOutbound(message)).isTrue();
        ByteBuf encoded = channel.readOutbound();
        try {
            assertThat(encoded.toString(CharsetUtil.US_ASCII))
                    .isEqualTo("PROXY TCP6 2001:db8::1 2001:db8::2 12345 8443\r\n");
        } finally {
            encoded.release();
            assertThat(channel.finish()).isFalse();
        }
    }

    @Test
    void decodesBinaryTcp4HeaderWithSimpleTlvs() {
        ByteBuf header = Unpooled.buffer();
        header.writeBytes(BINARY_PREFIX);
        header.writeByte(0x21);
        header.writeByte(0x11);
        header.writeShort(31);
        header.writeBytes(new byte[] {(byte) 203, 0, 113, 1});
        header.writeBytes(new byte[] {(byte) 198, 51, 100, 10});
        header.writeShort(56324);
        header.writeShort(443);
        writeAsciiTlv(header, HAProxyTLV.Type.PP2_TYPE_ALPN, "h2");
        writeAsciiTlv(header, HAProxyTLV.Type.PP2_TYPE_AUTHORITY, "example.com");

        EmbeddedChannel channel = new EmbeddedChannel(new HAProxyMessageDecoder());
        assertThat(channel.writeInbound(header)).isTrue();
        HAProxyMessage message = channel.readInbound();
        try {
            assertThat(message.protocolVersion()).isEqualTo(HAProxyProtocolVersion.V2);
            assertThat(message.command()).isEqualTo(HAProxyCommand.PROXY);
            assertThat(message.proxiedProtocol()).isEqualTo(HAProxyProxiedProtocol.TCP4);
            assertThat(message.sourceAddress()).isEqualTo("203.0.113.1");
            assertThat(message.destinationAddress()).isEqualTo("198.51.100.10");
            assertThat(message.sourcePort()).isEqualTo(56324);
            assertThat(message.destinationPort()).isEqualTo(443);
            assertThat(message.tlvs()).hasSize(2);
            assertTlv(message.tlvs().get(0), HAProxyTLV.Type.PP2_TYPE_ALPN, "h2");
            assertTlv(message.tlvs().get(1), HAProxyTLV.Type.PP2_TYPE_AUTHORITY, "example.com");
        } finally {
            message.release();
            assertThat(channel.finish()).isFalse();
        }
    }

    @Test
    void decodesBinarySslTlvAndExposesNestedTlvs() {
        ByteBuf header = Unpooled.buffer();
        header.writeBytes(BINARY_PREFIX);
        header.writeByte(0x21);
        header.writeByte(0x11);
        header.writeShort(34);
        header.writeBytes(new byte[] {10, 0, 0, 1});
        header.writeBytes(new byte[] {10, 0, 0, 2});
        header.writeShort(1111);
        header.writeShort(2222);
        header.writeByte(HAProxyTLV.Type.byteValueForType(HAProxyTLV.Type.PP2_TYPE_SSL));
        header.writeShort(19);
        header.writeByte(0x07);
        header.writeInt(0);
        writeAsciiTlv(header, HAProxyTLV.Type.PP2_TYPE_SSL_VERSION, "TLSv1");
        writeAsciiTlv(header, HAProxyTLV.Type.PP2_TYPE_SSL_CN, "bob");

        EmbeddedChannel channel = new EmbeddedChannel(new HAProxyMessageDecoder());
        assertThat(channel.writeInbound(header)).isTrue();
        HAProxyMessage message = channel.readInbound();
        try {
            assertThat(message.tlvs()).hasSize(3);
            assertThat(message.tlvs().get(0)).isInstanceOf(HAProxySSLTLV.class);
            HAProxySSLTLV sslTlv = (HAProxySSLTLV) message.tlvs().get(0);
            assertThat(sslTlv.type()).isEqualTo(HAProxyTLV.Type.PP2_TYPE_SSL);
            assertThat(sslTlv.client()).isEqualTo((byte) 0x07);
            assertThat(sslTlv.verify()).isZero();
            assertThat(sslTlv.isPP2ClientSSL()).isTrue();
            assertThat(sslTlv.isPP2ClientCertConn()).isTrue();
            assertThat(sslTlv.isPP2ClientCertSess()).isTrue();
            assertThat(sslTlv.encapsulatedTLVs()).hasSize(2);
            assertTlv(message.tlvs().get(1), HAProxyTLV.Type.PP2_TYPE_SSL_VERSION, "TLSv1");
            assertTlv(message.tlvs().get(2), HAProxyTLV.Type.PP2_TYPE_SSL_CN, "bob");
        } finally {
            message.release();
            assertThat(channel.finish()).isFalse();
        }
    }

    @Test
    void encodesThenDecodesBinaryTcp4HeaderWithTlvs() {
        List<HAProxyTLV> tlvs = Arrays.asList(
                new HAProxyTLV(
                        HAProxyTLV.Type.PP2_TYPE_ALPN,
                        Unpooled.copiedBuffer("http/1.1", CharsetUtil.US_ASCII)),
                new HAProxyTLV((byte) 0x55, Unpooled.copiedBuffer("custom-value", CharsetUtil.US_ASCII)));
        HAProxyMessage outboundMessage = new HAProxyMessage(
                HAProxyProtocolVersion.V2,
                HAProxyCommand.PROXY,
                HAProxyProxiedProtocol.TCP4,
                "192.0.2.1",
                "192.0.2.2",
                65535,
                80,
                tlvs);
        EmbeddedChannel encoder = new EmbeddedChannel(HAProxyMessageEncoder.INSTANCE);

        assertThat(encoder.writeOutbound(outboundMessage)).isTrue();
        ByteBuf encoded = encoder.readOutbound();
        assertThat(encoder.finish()).isFalse();

        EmbeddedChannel decoder = new EmbeddedChannel(new HAProxyMessageDecoder());
        assertThat(decoder.writeInbound(encoded)).isTrue();
        HAProxyMessage inboundMessage = decoder.readInbound();
        try {
            assertThat(inboundMessage.protocolVersion()).isEqualTo(HAProxyProtocolVersion.V2);
            assertThat(inboundMessage.command()).isEqualTo(HAProxyCommand.PROXY);
            assertThat(inboundMessage.proxiedProtocol()).isEqualTo(HAProxyProxiedProtocol.TCP4);
            assertThat(inboundMessage.sourceAddress()).isEqualTo("192.0.2.1");
            assertThat(inboundMessage.destinationAddress()).isEqualTo("192.0.2.2");
            assertThat(inboundMessage.sourcePort()).isEqualTo(65535);
            assertThat(inboundMessage.destinationPort()).isEqualTo(80);
            assertThat(inboundMessage.tlvs()).hasSize(2);
            assertTlv(inboundMessage.tlvs().get(0), HAProxyTLV.Type.PP2_TYPE_ALPN, "http/1.1");
            assertTlv(inboundMessage.tlvs().get(1), HAProxyTLV.Type.OTHER, "custom-value");
            assertThat(inboundMessage.tlvs().get(1).typeByteValue()).isEqualTo((byte) 0x55);
        } finally {
            inboundMessage.release();
            assertThat(decoder.finish()).isFalse();
        }
    }

    @Test
    void encodesThenDecodesBinaryUnixStreamHeader() {
        String sourceAddress = "/var/run/source.sock";
        String destinationAddress = "/var/run/destination.sock";
        HAProxyMessage outboundMessage = new HAProxyMessage(
                HAProxyProtocolVersion.V2,
                HAProxyCommand.PROXY,
                HAProxyProxiedProtocol.UNIX_STREAM,
                sourceAddress,
                destinationAddress,
                0,
                0);
        EmbeddedChannel encoder = new EmbeddedChannel(HAProxyMessageEncoder.INSTANCE);

        assertThat(encoder.writeOutbound(outboundMessage)).isTrue();
        ByteBuf encoded = encoder.readOutbound();
        assertThat(encoder.finish()).isFalse();

        EmbeddedChannel decoder = new EmbeddedChannel(new HAProxyMessageDecoder());
        assertThat(decoder.writeInbound(encoded)).isTrue();
        HAProxyMessage inboundMessage = decoder.readInbound();
        try {
            assertThat(inboundMessage.protocolVersion()).isEqualTo(HAProxyProtocolVersion.V2);
            assertThat(inboundMessage.command()).isEqualTo(HAProxyCommand.PROXY);
            assertThat(inboundMessage.proxiedProtocol()).isEqualTo(HAProxyProxiedProtocol.UNIX_STREAM);
            assertThat(inboundMessage.sourceAddress()).isEqualTo(sourceAddress);
            assertThat(inboundMessage.destinationAddress()).isEqualTo(destinationAddress);
            assertThat(inboundMessage.sourcePort()).isZero();
            assertThat(inboundMessage.destinationPort()).isZero();
            assertThat(inboundMessage.tlvs()).isEmpty();
        } finally {
            inboundMessage.release();
            assertThat(decoder.finish()).isFalse();
        }
    }

    @Test
    void encodesThenDecodesBinaryLocalCommandWithoutProxyAddresses() {
        HAProxyMessage outboundMessage = new HAProxyMessage(
                HAProxyProtocolVersion.V2,
                HAProxyCommand.LOCAL,
                HAProxyProxiedProtocol.UNKNOWN,
                null,
                null,
                0,
                0);
        EmbeddedChannel encoder = new EmbeddedChannel(HAProxyMessageEncoder.INSTANCE);

        assertThat(encoder.writeOutbound(outboundMessage)).isTrue();
        ByteBuf encoded = encoder.readOutbound();
        assertThat(encoder.finish()).isFalse();

        EmbeddedChannel decoder = new EmbeddedChannel(new HAProxyMessageDecoder());
        assertThat(decoder.writeInbound(encoded)).isTrue();
        HAProxyMessage inboundMessage = decoder.readInbound();
        try {
            assertThat(inboundMessage.protocolVersion()).isEqualTo(HAProxyProtocolVersion.V2);
            assertThat(inboundMessage.command()).isEqualTo(HAProxyCommand.LOCAL);
            assertThat(inboundMessage.proxiedProtocol()).isEqualTo(HAProxyProxiedProtocol.UNKNOWN);
            assertThat(inboundMessage.sourceAddress()).isNull();
            assertThat(inboundMessage.destinationAddress()).isNull();
            assertThat(inboundMessage.sourcePort()).isZero();
            assertThat(inboundMessage.destinationPort()).isZero();
            assertThat(inboundMessage.tlvs()).isEmpty();
        } finally {
            inboundMessage.release();
            assertThat(decoder.finish()).isFalse();
        }
    }

    @Test
    void decodesUnknownTextProtocolAndRejectsInvalidHeaders() {
        EmbeddedChannel unknownChannel = new EmbeddedChannel(new HAProxyMessageDecoder());
        assertThat(unknownChannel.writeInbound(
                Unpooled.copiedBuffer("PROXY UNKNOWN\r\n", CharsetUtil.US_ASCII)))
                .isTrue();
        HAProxyMessage unknown = unknownChannel.readInbound();
        try {
            assertThat(unknown.protocolVersion()).isEqualTo(HAProxyProtocolVersion.V1);
            assertThat(unknown.proxiedProtocol()).isEqualTo(HAProxyProxiedProtocol.UNKNOWN);
            assertThat(unknown.sourceAddress()).isNull();
            assertThat(unknown.destinationAddress()).isNull();
            assertThat(unknown.sourcePort()).isZero();
            assertThat(unknown.destinationPort()).isZero();
        } finally {
            unknown.release();
            assertThat(unknownChannel.finish()).isFalse();
        }

        EmbeddedChannel invalidChannel = new EmbeddedChannel(new HAProxyMessageDecoder());
        assertThatThrownBy(() -> invalidChannel.writeInbound(
                Unpooled.copiedBuffer("PROXY TCP4 203.0.113.1 198.51.100.10 70000 443\r\n", CharsetUtil.US_ASCII)))
                .isInstanceOf(HAProxyProtocolException.class)
                .hasMessageContaining("invalid HAProxy message")
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("invalid port: 70000 (expected: 1 ~ 65535)");
        assertThat(invalidChannel.finish()).isFalse();
    }

    @Test
    void mapsPublicEnumByteValuesAndFamilies() {
        assertThat(HAProxyProtocolVersion.valueOf((byte) 0x10)).isEqualTo(HAProxyProtocolVersion.V1);
        assertThat(HAProxyProtocolVersion.valueOf((byte) 0x20)).isEqualTo(HAProxyProtocolVersion.V2);
        assertThat(HAProxyCommand.valueOf((byte) 0x00)).isEqualTo(HAProxyCommand.LOCAL);
        assertThat(HAProxyCommand.valueOf((byte) 0x01)).isEqualTo(HAProxyCommand.PROXY);

        assertThat(HAProxyProxiedProtocol.valueOf((byte) 0x11)).isEqualTo(HAProxyProxiedProtocol.TCP4);
        assertThat(HAProxyProxiedProtocol.valueOf((byte) 0x21)).isEqualTo(HAProxyProxiedProtocol.TCP6);
        assertThat(HAProxyProxiedProtocol.valueOf((byte) 0x12)).isEqualTo(HAProxyProxiedProtocol.UDP4);
        assertThat(HAProxyProxiedProtocol.valueOf((byte) 0x31)).isEqualTo(HAProxyProxiedProtocol.UNIX_STREAM);
        assertThat(HAProxyProxiedProtocol.TCP4.addressFamily())
                .isEqualTo(HAProxyProxiedProtocol.AddressFamily.AF_IPv4);
        assertThat(HAProxyProxiedProtocol.UDP6.transportProtocol())
                .isEqualTo(HAProxyProxiedProtocol.TransportProtocol.DGRAM);

        assertThat(HAProxyTLV.Type.typeForByteValue((byte) 0x01)).isEqualTo(HAProxyTLV.Type.PP2_TYPE_ALPN);
        assertThat(HAProxyTLV.Type.typeForByteValue((byte) 0x22)).isEqualTo(HAProxyTLV.Type.PP2_TYPE_SSL_CN);
        assertThat(HAProxyTLV.Type.typeForByteValue((byte) 0x7f)).isEqualTo(HAProxyTLV.Type.OTHER);
        assertThat(HAProxyTLV.Type.byteValueForType(HAProxyTLV.Type.PP2_TYPE_NETNS)).isEqualTo((byte) 0x30);

        assertThatThrownBy(() -> HAProxyCommand.valueOf((byte) 0x0f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HAProxyProtocolVersion.valueOf((byte) 0x30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertDetection(
            ByteBuf buffer,
            ProtocolDetectionState expectedState,
            HAProxyProtocolVersion expectedVersion) {
        int readerIndex = buffer.readerIndex();
        ProtocolDetectionResult<HAProxyProtocolVersion> result = HAProxyMessageDecoder.detectProtocol(buffer);

        assertThat(result.state()).isEqualTo(expectedState);
        assertThat(result.detectedProtocol()).isEqualTo(expectedVersion);
        assertThat(buffer.readerIndex()).isEqualTo(readerIndex);
    }

    private static void writeAsciiTlv(ByteBuf buffer, HAProxyTLV.Type type, String value) {
        buffer.writeByte(HAProxyTLV.Type.byteValueForType(type));
        buffer.writeShort(value.length());
        buffer.writeCharSequence(value, CharsetUtil.US_ASCII);
    }

    private static void assertTlv(HAProxyTLV tlv, HAProxyTLV.Type expectedType, String expectedValue) {
        assertThat(tlv.type()).isEqualTo(expectedType);
        assertThat(tlv.content().toString(CharsetUtil.US_ASCII)).isEqualTo(expectedValue);
    }
}
