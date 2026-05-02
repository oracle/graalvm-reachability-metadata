/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_socks;

import java.net.InetAddress;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandRequest;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4ClientDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ClientEncoder;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressDecoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressEncoder;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Netty_codec_socksTest {
    @Test
    void socks4MessagesRoundTripThroughClientAndServerCodecs() {
        EmbeddedChannel clientEncoder = new EmbeddedChannel(Socks4ClientEncoder.INSTANCE);
        EmbeddedChannel serverDecoder = new EmbeddedChannel(new Socks4ServerDecoder());
        EmbeddedChannel serverEncoder = new EmbeddedChannel(Socks4ServerEncoder.INSTANCE);
        EmbeddedChannel clientDecoder = new EmbeddedChannel(new Socks4ClientDecoder());
        try {
            Socks4CommandRequest request = new DefaultSocks4CommandRequest(
                    Socks4CommandType.CONNECT, "192.0.2.10", 1080, "netty-user");

            Socks4CommandRequest decodedRequest = decode(
                    serverDecoder, encode(clientEncoder, request), Socks4CommandRequest.class);

            assertThat(decodedRequest.decoderResult().isSuccess()).isTrue();
            assertThat(decodedRequest.version()).isEqualTo(SocksVersion.SOCKS4a);
            assertThat(decodedRequest.type()).isEqualTo(Socks4CommandType.CONNECT);
            assertThat(decodedRequest.dstAddr()).isEqualTo("192.0.2.10");
            assertThat(decodedRequest.dstPort()).isEqualTo(1080);
            assertThat(decodedRequest.userId()).isEqualTo("netty-user");

            Socks4CommandResponse response = new DefaultSocks4CommandResponse(
                    Socks4CommandStatus.SUCCESS, "198.51.100.20", 8080);

            Socks4CommandResponse decodedResponse = decode(
                    clientDecoder, encode(serverEncoder, response), Socks4CommandResponse.class);

            assertThat(decodedResponse.decoderResult().isSuccess()).isTrue();
            assertThat(decodedResponse.version()).isEqualTo(SocksVersion.SOCKS4a);
            assertThat(decodedResponse.status()).isEqualTo(Socks4CommandStatus.SUCCESS);
            assertThat(decodedResponse.status().isSuccess()).isTrue();
            assertThat(decodedResponse.dstAddr()).isEqualTo("198.51.100.20");
            assertThat(decodedResponse.dstPort()).isEqualTo(8080);
        } finally {
            clientEncoder.finishAndReleaseAll();
            serverDecoder.finishAndReleaseAll();
            serverEncoder.finishAndReleaseAll();
            clientDecoder.finishAndReleaseAll();
        }
    }

    @Test
    void socks4aCommandRequestSupportsDomainNames() {
        EmbeddedChannel clientEncoder = new EmbeddedChannel(Socks4ClientEncoder.INSTANCE);
        EmbeddedChannel serverDecoder = new EmbeddedChannel(new Socks4ServerDecoder());
        try {
            Socks4CommandRequest request = new DefaultSocks4CommandRequest(
                    Socks4CommandType.BIND, "destination.example.test", 9020, "domain-user");

            Socks4CommandRequest decodedRequest = decode(
                    serverDecoder, encode(clientEncoder, request), Socks4CommandRequest.class);

            assertThat(decodedRequest.decoderResult().isSuccess()).isTrue();
            assertThat(decodedRequest.version()).isEqualTo(SocksVersion.SOCKS4a);
            assertThat(decodedRequest.type()).isEqualTo(Socks4CommandType.BIND);
            assertThat(decodedRequest.dstAddr()).isEqualTo("destination.example.test");
            assertThat(decodedRequest.dstPort()).isEqualTo(9020);
            assertThat(decodedRequest.userId()).isEqualTo("domain-user");
        } finally {
            clientEncoder.finishAndReleaseAll();
            serverDecoder.finishAndReleaseAll();
        }
    }

    @Test
    void socks5InitialNegotiationAndPasswordAuthenticationRoundTrip() {
        EmbeddedChannel clientEncoder = new EmbeddedChannel(Socks5ClientEncoder.DEFAULT);
        EmbeddedChannel serverEncoder = new EmbeddedChannel(Socks5ServerEncoder.DEFAULT);
        EmbeddedChannel initialRequestDecoder = new EmbeddedChannel(new Socks5InitialRequestDecoder());
        EmbeddedChannel initialResponseDecoder = new EmbeddedChannel(new Socks5InitialResponseDecoder());
        EmbeddedChannel passwordRequestDecoder = new EmbeddedChannel(new Socks5PasswordAuthRequestDecoder());
        EmbeddedChannel passwordResponseDecoder = new EmbeddedChannel(new Socks5PasswordAuthResponseDecoder());
        try {
            Socks5InitialRequest initialRequest = new DefaultSocks5InitialRequest(
                    Socks5AuthMethod.NO_AUTH, Socks5AuthMethod.PASSWORD);

            Socks5InitialRequest decodedInitialRequest = decode(
                    initialRequestDecoder, encode(clientEncoder, initialRequest), Socks5InitialRequest.class);

            assertThat(decodedInitialRequest.decoderResult().isSuccess()).isTrue();
            assertThat(decodedInitialRequest.version()).isEqualTo(SocksVersion.SOCKS5);
            assertThat(decodedInitialRequest.authMethods())
                    .containsExactly(Socks5AuthMethod.NO_AUTH, Socks5AuthMethod.PASSWORD);

            Socks5InitialResponse decodedInitialResponse = decode(
                    initialResponseDecoder,
                    encode(serverEncoder, new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD)),
                    Socks5InitialResponse.class);

            assertThat(decodedInitialResponse.decoderResult().isSuccess()).isTrue();
            assertThat(decodedInitialResponse.authMethod()).isEqualTo(Socks5AuthMethod.PASSWORD);

            Socks5PasswordAuthRequest decodedPasswordRequest = decode(
                    passwordRequestDecoder,
                    encode(clientEncoder, new DefaultSocks5PasswordAuthRequest("scott", "tiger")),
                    Socks5PasswordAuthRequest.class);

            assertThat(decodedPasswordRequest.decoderResult().isSuccess()).isTrue();
            assertThat(decodedPasswordRequest.username()).isEqualTo("scott");
            assertThat(decodedPasswordRequest.password()).isEqualTo("tiger");

            Socks5PasswordAuthResponse decodedPasswordResponse = decode(
                    passwordResponseDecoder,
                    encode(serverEncoder, new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS)),
                    Socks5PasswordAuthResponse.class);

            assertThat(decodedPasswordResponse.decoderResult().isSuccess()).isTrue();
            assertThat(decodedPasswordResponse.status()).isEqualTo(Socks5PasswordAuthStatus.SUCCESS);
            assertThat(decodedPasswordResponse.status().isSuccess()).isTrue();
        } finally {
            clientEncoder.finishAndReleaseAll();
            serverEncoder.finishAndReleaseAll();
            initialRequestDecoder.finishAndReleaseAll();
            initialResponseDecoder.finishAndReleaseAll();
            passwordRequestDecoder.finishAndReleaseAll();
            passwordResponseDecoder.finishAndReleaseAll();
        }
    }

    @Test
    void socks5CommandCodecsSupportIpv4DomainAndIpv6Addresses() throws Exception {
        assertCommandRequestRoundTrip(Socks5AddressType.IPv4, "203.0.113.7", 443);
        assertCommandRequestRoundTrip(Socks5AddressType.DOMAIN, "service.example.test", 8443);
        assertCommandRequestRoundTrip(Socks5AddressType.IPv6, "2001:db8:0:0:0:0:0:1", 9443);

        assertCommandResponseRoundTrip(Socks5AddressType.IPv4, "192.0.2.55", 53);
        assertCommandResponseRoundTrip(Socks5AddressType.DOMAIN, "bind.example.test", 5353);
        assertCommandResponseRoundTrip(Socks5AddressType.IPv6, "2001:db8:0:0:0:0:0:2", 9050);
    }

    @Test
    void portUnificationServerHandlerSelectsSocks4AndSocks5Decoders() {
        EmbeddedChannel socks4Encoder = new EmbeddedChannel(Socks4ClientEncoder.INSTANCE);
        EmbeddedChannel socks4UnifiedServer = new EmbeddedChannel(new SocksPortUnificationServerHandler());
        EmbeddedChannel socks5Encoder = new EmbeddedChannel(Socks5ClientEncoder.DEFAULT);
        EmbeddedChannel socks5UnifiedServer = new EmbeddedChannel(new SocksPortUnificationServerHandler());
        try {
            Socks4CommandRequest socks4Request = decode(
                    socks4UnifiedServer,
                    encode(socks4Encoder, new DefaultSocks4CommandRequest(Socks4CommandType.BIND, "192.0.2.1", 22)),
                    Socks4CommandRequest.class);

            assertThat(socks4Request.decoderResult().isSuccess()).isTrue();
            assertThat(socks4Request.version()).isEqualTo(SocksVersion.SOCKS4a);
            assertThat(socks4Request.type()).isEqualTo(Socks4CommandType.BIND);
            assertThat(socks4Request.dstAddr()).isEqualTo("192.0.2.1");
            assertThat(socks4Request.dstPort()).isEqualTo(22);

            Socks5InitialRequest socks5Request = decode(
                    socks5UnifiedServer,
                    encode(socks5Encoder, new DefaultSocks5InitialRequest(Socks5AuthMethod.NO_AUTH)),
                    Socks5InitialRequest.class);

            assertThat(socks5Request.decoderResult().isSuccess()).isTrue();
            assertThat(socks5Request.version()).isEqualTo(SocksVersion.SOCKS5);
            assertThat(socks5Request.authMethods()).containsExactly(Socks5AuthMethod.NO_AUTH);
        } finally {
            socks4Encoder.finishAndReleaseAll();
            socks4UnifiedServer.finishAndReleaseAll();
            socks5Encoder.finishAndReleaseAll();
            socks5UnifiedServer.finishAndReleaseAll();
        }
    }

    @Test
    void socks5CommandCodecsUseCustomAddressCodecs() {
        Socks5AddressEncoder aliasingEncoder = (addressType, address, out) -> {
            String encodedAddress = Socks5AddressType.DOMAIN.equals(addressType) && "alias.example.test".equals(address)
                    ? "resolved.example.test"
                    : address;
            Socks5AddressEncoder.DEFAULT.encodeAddress(addressType, encodedAddress, out);
        };
        Socks5AddressDecoder taggingDecoder = (addressType, in) ->
                "decoded:" + Socks5AddressDecoder.DEFAULT.decodeAddress(addressType, in);

        EmbeddedChannel clientEncoder = new EmbeddedChannel(new Socks5ClientEncoder(aliasingEncoder));
        EmbeddedChannel serverDecoder = new EmbeddedChannel(new Socks5CommandRequestDecoder(taggingDecoder));
        EmbeddedChannel serverEncoder = new EmbeddedChannel(new Socks5ServerEncoder(aliasingEncoder));
        EmbeddedChannel clientDecoder = new EmbeddedChannel(new Socks5CommandResponseDecoder(taggingDecoder));
        try {
            Socks5CommandRequest request = new DefaultSocks5CommandRequest(
                    Socks5CommandType.CONNECT, Socks5AddressType.DOMAIN, "alias.example.test", 443);

            Socks5CommandRequest decodedRequest = decode(
                    serverDecoder, encode(clientEncoder, request), Socks5CommandRequest.class);

            assertThat(decodedRequest.decoderResult().isSuccess()).isTrue();
            assertThat(decodedRequest.dstAddrType()).isEqualTo(Socks5AddressType.DOMAIN);
            assertThat(decodedRequest.dstAddr()).isEqualTo("decoded:resolved.example.test");
            assertThat(decodedRequest.dstPort()).isEqualTo(443);

            Socks5CommandResponse response = new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.SUCCESS, Socks5AddressType.DOMAIN, "alias.example.test", 8443);

            Socks5CommandResponse decodedResponse = decode(
                    clientDecoder, encode(serverEncoder, response), Socks5CommandResponse.class);

            assertThat(decodedResponse.decoderResult().isSuccess()).isTrue();
            assertThat(decodedResponse.bndAddrType()).isEqualTo(Socks5AddressType.DOMAIN);
            assertThat(decodedResponse.bndAddr()).isEqualTo("decoded:resolved.example.test");
            assertThat(decodedResponse.bndPort()).isEqualTo(8443);
        } finally {
            clientEncoder.finishAndReleaseAll();
            serverDecoder.finishAndReleaseAll();
            serverEncoder.finishAndReleaseAll();
            clientDecoder.finishAndReleaseAll();
        }
    }

    @Test
    void replayingDecodersWaitForCompleteFramesBeforeEmittingMessages() {
        EmbeddedChannel passwordRequestDecoder = new EmbeddedChannel(new Socks5PasswordAuthRequestDecoder());
        try {
            ByteBuf firstFragment = Unpooled.copiedBuffer(new byte[] {0x01, 0x04, 'u' });
            ByteBuf secondFragment = Unpooled.copiedBuffer(
                    new byte[] {'s', 'e', 'r', 0x06, 's', 'e', 'c', 'r', 'e', 't' });

            assertThat(passwordRequestDecoder.writeInbound(firstFragment)).isFalse();
            assertThat((Object) passwordRequestDecoder.readInbound()).isNull();
            assertThat(passwordRequestDecoder.writeInbound(secondFragment)).isTrue();

            Socks5PasswordAuthRequest request = passwordRequestDecoder.readInbound();
            assertThat(request.decoderResult().isSuccess()).isTrue();
            assertThat(request.username()).isEqualTo("user");
            assertThat(request.password()).isEqualTo("secret");
            assertThat((Object) passwordRequestDecoder.readInbound()).isNull();
        } finally {
            passwordRequestDecoder.finishAndReleaseAll();
        }
    }

    @Test
    void protocolConstantsAndExtensibleValueObjectsExposeWireValues() {
        assertThat(SocksVersion.valueOf((byte) 0x04)).isEqualTo(SocksVersion.SOCKS4a);
        assertThat(SocksVersion.valueOf((byte) 0x05)).isEqualTo(SocksVersion.SOCKS5);
        assertThat(SocksVersion.valueOf((byte) 0x7f)).isEqualTo(SocksVersion.UNKNOWN);
        assertThat(SocksVersion.SOCKS5.byteValue()).isEqualTo((byte) 0x05);

        assertThat(Socks4CommandType.valueOf((byte) 0x01)).isEqualTo(Socks4CommandType.CONNECT);
        assertThat(Socks4CommandType.valueOf((byte) 0x02)).isEqualTo(Socks4CommandType.BIND);
        assertThat(new Socks4CommandType(0x7e, "PRIVATE").byteValue()).isEqualTo((byte) 0x7e);
        assertThat(Socks4CommandStatus.SUCCESS.isSuccess()).isTrue();
        assertThat(Socks4CommandStatus.REJECTED_OR_FAILED.isSuccess()).isFalse();

        assertThat(Socks5AuthMethod.valueOf((byte) 0x00)).isEqualTo(Socks5AuthMethod.NO_AUTH);
        assertThat(Socks5AuthMethod.valueOf((byte) 0x02)).isEqualTo(Socks5AuthMethod.PASSWORD);
        assertThat(new Socks5AuthMethod(0x80, "PRIVATE")).isEqualTo(Socks5AuthMethod.valueOf((byte) 0x80));
        assertThat(Socks5CommandType.valueOf((byte) 0x03)).isEqualTo(Socks5CommandType.UDP_ASSOCIATE);
        assertThat(Socks5AddressType.valueOf((byte) 0x03)).isEqualTo(Socks5AddressType.DOMAIN);
        assertThat(Socks5CommandStatus.SUCCESS.isSuccess()).isTrue();
        assertThat(Socks5CommandStatus.ADDRESS_UNSUPPORTED.isSuccess()).isFalse();
        assertThat(Socks5PasswordAuthStatus.SUCCESS.isSuccess()).isTrue();
        assertThat(Socks5PasswordAuthStatus.FAILURE.isSuccess()).isFalse();
    }

    private static void assertCommandRequestRoundTrip(Socks5AddressType addressType, String address, int port)
            throws Exception {
        EmbeddedChannel clientEncoder = new EmbeddedChannel(Socks5ClientEncoder.DEFAULT);
        EmbeddedChannel serverDecoder = new EmbeddedChannel(new Socks5CommandRequestDecoder());
        try {
            Socks5CommandRequest request = new DefaultSocks5CommandRequest(
                    Socks5CommandType.CONNECT, addressType, address, port);

            Socks5CommandRequest decodedRequest = decode(
                    serverDecoder, encode(clientEncoder, request), Socks5CommandRequest.class);

            assertThat(decodedRequest.decoderResult().isSuccess()).isTrue();
            assertThat(decodedRequest.version()).isEqualTo(SocksVersion.SOCKS5);
            assertThat(decodedRequest.type()).isEqualTo(Socks5CommandType.CONNECT);
            assertThat(decodedRequest.dstAddrType()).isEqualTo(addressType);
            assertAddressEquals(addressType, address, decodedRequest.dstAddr());
            assertThat(decodedRequest.dstPort()).isEqualTo(port);
        } finally {
            clientEncoder.finishAndReleaseAll();
            serverDecoder.finishAndReleaseAll();
        }
    }

    private static void assertCommandResponseRoundTrip(Socks5AddressType addressType, String address, int port)
            throws Exception {
        EmbeddedChannel serverEncoder = new EmbeddedChannel(Socks5ServerEncoder.DEFAULT);
        EmbeddedChannel clientDecoder = new EmbeddedChannel(new Socks5CommandResponseDecoder());
        try {
            Socks5CommandResponse response = new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.SUCCESS, addressType, address, port);

            Socks5CommandResponse decodedResponse = decode(
                    clientDecoder, encode(serverEncoder, response), Socks5CommandResponse.class);

            assertThat(decodedResponse.decoderResult().isSuccess()).isTrue();
            assertThat(decodedResponse.version()).isEqualTo(SocksVersion.SOCKS5);
            assertThat(decodedResponse.status()).isEqualTo(Socks5CommandStatus.SUCCESS);
            assertThat(decodedResponse.status().isSuccess()).isTrue();
            assertThat(decodedResponse.bndAddrType()).isEqualTo(addressType);
            assertAddressEquals(addressType, address, decodedResponse.bndAddr());
            assertThat(decodedResponse.bndPort()).isEqualTo(port);
        } finally {
            serverEncoder.finishAndReleaseAll();
            clientDecoder.finishAndReleaseAll();
        }
    }

    private static void assertAddressEquals(Socks5AddressType addressType, String expected, String actual)
            throws Exception {
        if (Socks5AddressType.IPv6.equals(addressType)) {
            assertThat(InetAddress.getByName(actual)).isEqualTo(InetAddress.getByName(expected));
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }

    private static ByteBuf encode(EmbeddedChannel channel, Object message) {
        assertThat(channel.writeOutbound(message)).isTrue();
        ByteBuf encoded = channel.readOutbound();
        assertThat(encoded).isNotNull();
        assertThat((Object) channel.readOutbound()).isNull();
        return encoded;
    }

    private static <T> T decode(EmbeddedChannel channel, ByteBuf encoded, Class<T> messageType) {
        assertThat(channel.writeInbound(encoded)).isTrue();
        Object decoded = channel.readInbound();
        assertThat(decoded).isInstanceOf(messageType);
        assertThat((Object) channel.readInbound()).isNull();
        return messageType.cast(decoded);
    }

}
