/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DNS message assembly subsystem: a caching proxy builds a client response and
 * replaces stale answer data before forwarding the message.
 */
class DnsMessageAssemblyTest {

    @Test
    void proxyReplacesStaleAnswerAndRemovesAuthorityData() {
        DnsQuery query = new DefaultDnsQuery(0x5319)
                .setRecursionDesired(true)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("service.example.test", DnsRecordType.A));
        DnsResponse response = new DefaultDnsResponse(query.id())
                .setRecursionDesired(query.isRecursionDesired())
                .setRecursionAvailable(true)
                .setCode(DnsResponseCode.NOERROR)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("service.example.test", DnsRecordType.A));
        DefaultDnsRawRecord staleAnswer = new DefaultDnsRawRecord(
                "service.example.test", DnsRecordType.A, 15,
                Unpooled.wrappedBuffer(new byte[] {(byte) 192, 0, 2, 10}));
        DefaultDnsRawRecord authority = new DefaultDnsRawRecord(
                "example.test", DnsRecordType.NS, 60,
                Unpooled.wrappedBuffer(new byte[] {1, 2, 3}));
        DefaultDnsRawRecord freshAnswer = new DefaultDnsRawRecord(
                "service.example.test", DnsRecordType.A, 120,
                Unpooled.wrappedBuffer(new byte[] {(byte) 192, 0, 2, 25}));

        try {
            response.addRecord(DnsSection.ANSWER, staleAnswer)
                    .addRecord(DnsSection.AUTHORITY, authority);

            DnsRecord replaced = response.setRecord(DnsSection.ANSWER, 0, freshAnswer);
            assertThat(replaced).isSameAs(staleAnswer);
            ReferenceCountUtil.release(replaced);

            DnsRecord removedAuthority = response.removeRecord(DnsSection.AUTHORITY, 0);
            assertThat(removedAuthority).isSameAs(authority);
            ReferenceCountUtil.release(removedAuthority);

            assertThat(response.id()).isEqualTo(0x5319);
            assertThat(response.count(DnsSection.QUESTION)).isOne();
            assertThat(response.count(DnsSection.ANSWER)).isOne();
            assertThat(response.count(DnsSection.AUTHORITY)).isZero();
            assertThat(response.<DefaultDnsRawRecord>recordAt(DnsSection.ANSWER)
                    .timeToLive()).isEqualTo(120);
            assertThat(response.<DefaultDnsRawRecord>recordAt(DnsSection.ANSWER)
                    .content().readableBytes()).isEqualTo(4);
            assertThat(response.<DefaultDnsRawRecord>recordAt(DnsSection.ANSWER)
                    .content().getUnsignedByte(3)).isEqualTo((short) 25);
        } finally {
            response.release();
            query.release();
        }
    }
}
