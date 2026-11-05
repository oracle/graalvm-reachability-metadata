/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsQueryDecoder;
import io.netty.handler.codec.dns.TcpDnsQueryEncoder;
import io.netty.handler.codec.dns.TcpDnsResponseDecoder;
import io.netty.handler.codec.dns.TcpDnsResponseEncoder;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the TCP stream codec subsystem: an application exchanges DNS messages over a
 * length-prefixed TCP connection and reads the resolver's answer records.
 */
class DnsTcpStreamCodecTest {

    @Test
    void applicationQuerySurvivesTcpFraming() {
        DnsQuery query = new DefaultDnsQuery(0x2357)
                .setRecursionDesired(true)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("service.example.test", DnsRecordType.AAAA));
        EmbeddedChannel encoder = new EmbeddedChannel(new TcpDnsQueryEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(new TcpDnsQueryDecoder());

        try {
            assertThat(encoder.writeOutbound(query)).isTrue();
            ByteBuf wire = encoder.readOutbound();
            assertThat(wire.readUnsignedShort()).isEqualTo(wire.readableBytes());
            wire.readerIndex(0);
            assertThat(decoder.writeInbound(wire.readRetainedSlice(1))).isFalse();
            assertThat(decoder.writeInbound(wire)).isTrue();

            DnsQuery decoded = decoder.readInbound();
            assertThat(decoded.id()).isEqualTo(0x2357);
            assertThat(decoded.isRecursionDesired()).isTrue();
            assertThat(decoded.count(DnsSection.QUESTION)).isOne();
            assertThat(decoded.<DefaultDnsQuestion>recordAt(DnsSection.QUESTION).name())
                    .isEqualTo("service.example.test.");
            assertThat(decoded.<DefaultDnsQuestion>recordAt(DnsSection.QUESTION).type())
                    .isEqualTo(DnsRecordType.AAAA);
            ReferenceCountUtil.release(decoded);
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

    @Test
    void applicationReadsAddressFromTcpResponse() {
        DnsResponse response = new DefaultDnsResponse(0x2357)
                .setRecursionAvailable(true)
                .setCode(DnsResponseCode.NOERROR)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("service.example.test", DnsRecordType.A))
                .addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(
                        "service.example.test", DnsRecordType.A, 60,
                        Unpooled.wrappedBuffer(new byte[] {(byte) 192, 0, 2, 77})));
        EmbeddedChannel encoder = new EmbeddedChannel(new TcpDnsResponseEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(new TcpDnsResponseDecoder());

        try {
            assertThat(encoder.writeOutbound(response)).isTrue();
            ByteBuf wire = encoder.readOutbound();
            assertThat(decoder.writeInbound(wire)).isTrue();

            DnsResponse decoded = decoder.readInbound();
            assertThat(decoded.id()).isEqualTo(0x2357);
            assertThat(decoded.isRecursionAvailable()).isTrue();
            assertThat(decoded.code()).isEqualTo(DnsResponseCode.NOERROR);
            assertThat(decoded.count(DnsSection.ANSWER)).isOne();
            DefaultDnsRawRecord answer = decoded.recordAt(DnsSection.ANSWER);
            assertThat(answer.name()).isEqualTo("service.example.test.");
            assertThat(answer.timeToLive()).isEqualTo(60);
            assertThat(answer.content().readableBytes()).isEqualTo(4);
            assertThat(answer.content().readUnsignedByte()).isEqualTo((short) 192);
            assertThat(answer.content().readUnsignedByte()).isEqualTo((short) 0);
            assertThat(answer.content().readUnsignedByte()).isEqualTo((short) 2);
            assertThat(answer.content().readUnsignedByte()).isEqualTo((short) 77);
            ReferenceCountUtil.release(decoded);
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }
}
