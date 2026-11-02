/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsResponseDecoder;
import io.netty.handler.codec.dns.TcpDnsResponseEncoder;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DNS truncated-response fallback subsystem: a resolver switches from UDP to TCP
 * when the UDP response signals truncation and then returns the complete address answer.
 */
public class DnsTruncatedResponseFallbackTest {

    private static final int QUERY_ID = 0x6E10;

    private static final InetSocketAddress CLIENT = new InetSocketAddress("127.0.0.1", 49152);

    @Test
    void resolverRetriesTruncatedUdpResponseOverTcp() {
        DnsResponse truncatedResponse = new DefaultDnsResponse(QUERY_ID)
                .setCode(DnsResponseCode.NOERROR)
                .setTruncated(true)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("large.example.test", DnsRecordType.A));
        EmbeddedChannel udpEncoder = new EmbeddedChannel(new DatagramDnsResponseEncoder());
        EmbeddedChannel udpDecoder = new EmbeddedChannel(new DatagramDnsResponseDecoder());

        try {
            assertThat(udpEncoder.writeOutbound(
                    new DefaultAddressedEnvelope<>(truncatedResponse, CLIENT))).isTrue();
            DatagramPacket udpWire = udpEncoder.readOutbound();
            assertThat(udpDecoder.writeInbound(udpWire)).isTrue();
            DnsResponse decodedUdpResponse = udpDecoder.readInbound();
            try {
                assertThat(decodedUdpResponse.id()).isEqualTo(QUERY_ID);
                assertThat(decodedUdpResponse.isTruncated()).isTrue();
                assertThat(decodedUdpResponse.count(DnsSection.ANSWER)).isZero();
            } finally {
                decodedUdpResponse.release();
            }
        } finally {
            udpEncoder.finishAndReleaseAll();
            udpDecoder.finishAndReleaseAll();
        }

        DnsResponse completeResponse = new DefaultDnsResponse(QUERY_ID)
                .setCode(DnsResponseCode.NOERROR)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("large.example.test", DnsRecordType.A))
                .addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(
                        "large.example.test", DnsRecordType.A, 60,
                        Unpooled.wrappedBuffer(new byte[] {(byte) 192, 0, 2, (byte) 140})));
        EmbeddedChannel tcpEncoder = new EmbeddedChannel(new TcpDnsResponseEncoder());
        EmbeddedChannel tcpDecoder = new EmbeddedChannel(new TcpDnsResponseDecoder());

        try {
            assertThat(tcpEncoder.writeOutbound(completeResponse)).isTrue();
            ByteBuf tcpWire = tcpEncoder.readOutbound();
            assertThat(tcpDecoder.writeInbound(tcpWire)).isTrue();
            DnsResponse decodedTcpResponse = tcpDecoder.readInbound();
            try {
                assertThat(decodedTcpResponse.id()).isEqualTo(QUERY_ID);
                assertThat(decodedTcpResponse.isTruncated()).isFalse();
                assertThat(decodedTcpResponse.count(DnsSection.ANSWER)).isOne();
                assertThat(decodedTcpResponse.<DefaultDnsRawRecord>recordAt(DnsSection.ANSWER)
                        .name()).isEqualTo("large.example.test.");
                assertThat(decodedTcpResponse.<DefaultDnsRawRecord>recordAt(DnsSection.ANSWER)
                        .content().readableBytes()).isEqualTo(4);
                assertThat(decodedTcpResponse.<DefaultDnsRawRecord>recordAt(DnsSection.ANSWER)
                        .content().readUnsignedByte()).isEqualTo((short) 192);
                assertThat(decodedTcpResponse.<DefaultDnsRawRecord>recordAt(DnsSection.ANSWER)
                        .content().readUnsignedByte()).isZero();
                assertThat(decodedTcpResponse.<DefaultDnsRawRecord>recordAt(DnsSection.ANSWER)
                        .content().readUnsignedByte()).isEqualTo((short) 2);
                assertThat(decodedTcpResponse.<DefaultDnsRawRecord>recordAt(DnsSection.ANSWER)
                        .content().readUnsignedByte()).isEqualTo((short) 140);
            } finally {
                decodedTcpResponse.release();
            }
        } finally {
            tcpEncoder.finishAndReleaseAll();
            tcpDecoder.finishAndReleaseAll();
        }
    }
}
