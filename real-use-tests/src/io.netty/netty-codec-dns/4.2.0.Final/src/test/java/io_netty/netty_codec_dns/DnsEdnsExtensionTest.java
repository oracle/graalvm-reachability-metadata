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
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import io.netty.handler.codec.dns.DefaultDnsOptEcsRecord;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the EDNS extension subsystem: a resolver returns client-subnet information alongside
 * an address answer, and an application reads both records from the decoded response.
 */
public class DnsEdnsExtensionTest {

    private static final InetSocketAddress CLIENT = new InetSocketAddress("127.0.0.1", 49152);

    @Test
    void applicationReadsEdnsClientSubnetFromResolverResponse() {
        DnsResponse response = new DefaultDnsResponse(0x4182)
                .setRecursionAvailable(true)
                .setCode(DnsResponseCode.NOERROR)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("service.example.test", DnsRecordType.A))
                .addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(
                        "service.example.test", DnsRecordType.A, 120,
                        Unpooled.wrappedBuffer(new byte[] {(byte) 192, 0, 2, 17})))
                .addRecord(DnsSection.ADDITIONAL, new DefaultDnsOptEcsRecord(
                        1232, 24, new byte[] {(byte) 192, 0, 2, 0}));
        EmbeddedChannel encoder = new EmbeddedChannel(new DatagramDnsResponseEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(new DatagramDnsResponseDecoder());

        try {
            assertThat(encoder.writeOutbound(new DefaultAddressedEnvelope<>(response, CLIENT))).isTrue();
            DatagramPacket wireResponse = encoder.readOutbound();
            assertThat(wireResponse.recipient()).isEqualTo(CLIENT);
            assertThat(decoder.writeInbound(wireResponse)).isTrue();

            DnsResponse decoded = decoder.readInbound();
            try {
                assertThat(decoded.id()).isEqualTo(0x4182);
                assertThat(decoded.code()).isEqualTo(DnsResponseCode.NOERROR);
                assertThat(decoded.count(DnsSection.ANSWER)).isOne();
                assertThat(decoded.<DefaultDnsRawRecord>recordAt(DnsSection.ANSWER)
                        .content().readableBytes()).isEqualTo(4);
                assertThat(decoded.count(DnsSection.ADDITIONAL)).isOne();
                DnsRawRecord clientSubnet = decoded.recordAt(DnsSection.ADDITIONAL);
                assertThat(clientSubnet.type()).isEqualTo(DnsRecordType.OPT);
                assertThat(clientSubnet.content().readableBytes()).isEqualTo(11);
                assertThat(clientSubnet.content().readUnsignedShort()).isEqualTo(8);
                assertThat(clientSubnet.content().readUnsignedShort()).isEqualTo(7);
                assertThat(clientSubnet.content().readUnsignedShort()).isEqualTo(1);
                assertThat(clientSubnet.content().readUnsignedByte()).isEqualTo((short) 24);
                assertThat(clientSubnet.content().readUnsignedByte()).isEqualTo((short) 0);
                assertThat(clientSubnet.content().readUnsignedByte()).isEqualTo((short) 192);
                assertThat(clientSubnet.content().readUnsignedByte()).isEqualTo((short) 0);
                assertThat(clientSubnet.content().readUnsignedByte()).isEqualTo((short) 2);
            } finally {
                ReferenceCountUtil.release(decoded);
            }
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }
}
