/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder;
import io.netty.handler.codec.dns.DatagramDnsQueryEncoder;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DNS datagram codec subsystem: an application query is encoded for a resolver,
 * and a resolver response containing an address record is decoded back into application data.
 */
class DnsResolverProtocolTest {

    private static final InetSocketAddress RESOLVER = new InetSocketAddress("127.0.0.1", 53);

    private static final InetSocketAddress CLIENT = new InetSocketAddress("127.0.0.1", 49152);

    @Test
    void applicationQueryRoundTripsThroughDatagramCodec() {
        DnsQuery request = new DefaultDnsQuery(0x4A21)
                .setRecursionDesired(true)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("api.example.test", DnsRecordType.A));
        EmbeddedChannel encoder = new EmbeddedChannel(new DatagramDnsQueryEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(new DatagramDnsQueryDecoder());

        try {
            assertThat(encoder.writeOutbound(new DefaultAddressedEnvelope<>(request, RESOLVER))).isTrue();
            DatagramPacket wireRequest = encoder.readOutbound();
            assertThat(wireRequest.recipient()).isEqualTo(RESOLVER);
            assertThat(decoder.writeInbound(wireRequest)).isTrue();

            DnsQuery decoded = decoder.readInbound();
            assertThat(decoded.id()).isEqualTo(0x4A21);
            assertThat(decoded.isRecursionDesired()).isTrue();
            assertThat(decoded.count(DnsSection.QUESTION)).isOne();
            assertThat(decoded.<DefaultDnsQuestion>recordAt(DnsSection.QUESTION).name())
                    .isEqualTo("api.example.test.");
            assertThat(decoded.<DefaultDnsQuestion>recordAt(DnsSection.QUESTION).type())
                    .isEqualTo(DnsRecordType.A);
            decoded.release();
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

    @Test
    void applicationResponseDecodesAddressRecordFromDatagram() {
        DnsResponse response = new DefaultDnsResponse(0x4A21)
                .setRecursionAvailable(true)
                .setCode(DnsResponseCode.NOERROR)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("api.example.test", DnsRecordType.A))
                .addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(
                        "api.example.test", DnsRecordType.A, 30,
                        Unpooled.wrappedBuffer(new byte[] {(byte) 192, 0, 2, 42})));
        EmbeddedChannel encoder = new EmbeddedChannel(new DatagramDnsResponseEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(new DatagramDnsResponseDecoder());

        try {
            assertThat(encoder.writeOutbound(new DefaultAddressedEnvelope<>(response, CLIENT))).isTrue();
            DatagramPacket wireResponse = encoder.readOutbound();
            assertThat(wireResponse.recipient()).isEqualTo(CLIENT);
            assertThat(decoder.writeInbound(wireResponse)).isTrue();

            DnsResponse decoded = decoder.readInbound();
            assertThat(decoded.id()).isEqualTo(0x4A21);
            assertThat(decoded.isRecursionAvailable()).isTrue();
            assertThat(decoded.code()).isEqualTo(DnsResponseCode.NOERROR);
            assertThat(decoded.count(DnsSection.ANSWER)).isOne();
            DefaultDnsRawRecord answer = decoded.recordAt(DnsSection.ANSWER);
            assertThat(answer.name()).isEqualTo("api.example.test.");
            assertThat(answer.timeToLive()).isEqualTo(30);
            assertThat(answer.content().readableBytes()).isEqualTo(4);
            assertThat(answer.content().readUnsignedByte()).isEqualTo((short) 192);
            assertThat(answer.content().readUnsignedByte()).isEqualTo((short) 0);
            assertThat(answer.content().readUnsignedByte()).isEqualTo((short) 2);
            assertThat(answer.content().readUnsignedByte()).isEqualTo((short) 42);
            ReferenceCountUtil.release(decoded);
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }
}
