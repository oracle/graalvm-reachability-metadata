/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecordDecoder;
import io.netty.handler.codec.dns.DnsRecordEncoder;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DNS record wire-codec subsystem: a gateway encodes a cached record and decodes it
 * when serving the same answer through a separate DNS transport.
 */
class DnsRecordWireCodecTest {

    @Test
    void gatewayRoundTripsQuestionAndAddressRecord() throws Exception {
        DnsQuestion question = new DefaultDnsQuestion("api.example.test", DnsRecordType.A);
        DnsRawRecord answer = new DefaultDnsRawRecord(
                "api.example.test", DnsRecordType.A, 90,
                Unpooled.wrappedBuffer(new byte[] {(byte) 192, 0, 2, 44}));
        ByteBuf wire = Unpooled.buffer();

        try {
            DnsRecordEncoder.DEFAULT.encodeQuestion(question, wire);
            DnsRecordEncoder.DEFAULT.encodeRecord(answer, wire);
            DnsQuestion decodedQuestion = DnsRecordDecoder.DEFAULT.decodeQuestion(wire);
            DnsRawRecord decodedAnswer = DnsRecordDecoder.DEFAULT.decodeRecord(wire);

            assertThat(decodedQuestion.name()).isEqualTo("api.example.test.");
            assertThat(decodedQuestion.type()).isEqualTo(DnsRecordType.A);
            assertThat(decodedAnswer.name()).isEqualTo("api.example.test.");
            assertThat(decodedAnswer.type()).isEqualTo(DnsRecordType.A);
            assertThat(decodedAnswer.timeToLive()).isEqualTo(90);
            assertThat(decodedAnswer.content().readableBytes()).isEqualTo(4);
            assertThat(decodedAnswer.content().readUnsignedByte()).isEqualTo((short) 192);
            assertThat(decodedAnswer.content().readUnsignedByte()).isEqualTo((short) 0);
            assertThat(decodedAnswer.content().readUnsignedByte()).isEqualTo((short) 2);
            assertThat(decodedAnswer.content().readUnsignedByte()).isEqualTo((short) 44);
            assertThat(wire.isReadable()).isFalse();
            ReferenceCountUtil.release(decodedAnswer);
        } finally {
            answer.release();
            wire.release();
        }
    }
}
