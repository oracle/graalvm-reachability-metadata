/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DNS datagram-envelope subsystem: a resolver receives an addressed query and
 * returns an addressed response with the application-visible record data.
 */
class DnsDatagramEnvelopeTest {

    private static final InetSocketAddress CLIENT = new InetSocketAddress("127.0.0.1", 49152);

    private static final InetSocketAddress RESOLVER = new InetSocketAddress("127.0.0.1", 53);

    @Test
    void resolverKeepsDatagramAddressesWhenBuildingResponse() {
        DatagramDnsQuery query = new DatagramDnsQuery(CLIENT, RESOLVER, 0x2C91)
                .setRecursionDesired(true)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("app.example.test", DnsRecordType.A));

        try {
            assertThat(query.sender()).isEqualTo(CLIENT);
            assertThat(query.recipient()).isEqualTo(RESOLVER);
            assertThat(query.id()).isEqualTo(0x2C91);
            assertThat(query.isRecursionDesired()).isTrue();
            assertThat(query.count(DnsSection.QUESTION)).isOne();
            DnsQuestion question = query.recordAt(DnsSection.QUESTION);
            assertThat(question.name()).isEqualTo("app.example.test.");
            assertThat(question.type()).isEqualTo(DnsRecordType.A);

            DatagramDnsResponse response = new DatagramDnsResponse(RESOLVER, CLIENT, query.id())
                    .setAuthoritativeAnswer(true)
                    .setRecursionAvailable(true)
                    .setCode(DnsResponseCode.NOERROR)
                    .addRecord(DnsSection.QUESTION, question)
                    .addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(
                            "app.example.test", DnsRecordType.A, 120,
                            Unpooled.wrappedBuffer(new byte[] {(byte) 192, 0, 2, 88})));
            try {
                assertThat(response.sender()).isEqualTo(RESOLVER);
                assertThat(response.recipient()).isEqualTo(CLIENT);
                assertThat(response.id()).isEqualTo(query.id());
                assertThat(response.isAuthoritativeAnswer()).isTrue();
                assertThat(response.isRecursionAvailable()).isTrue();
                assertThat(response.code()).isEqualTo(DnsResponseCode.NOERROR);
                assertThat(response.count(DnsSection.ANSWER)).isOne();
                assertThat(response.recordAt(DnsSection.ANSWER).name())
                        .isEqualTo("app.example.test.");
                assertThat(response.<DefaultDnsRawRecord>recordAt(DnsSection.ANSWER)
                        .content().readableBytes()).isEqualTo(4);
            } finally {
                response.release();
            }
        } finally {
            query.release();
        }
    }
}
