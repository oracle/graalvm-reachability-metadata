/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DNS record snapshot subsystem: a cache keeps a decoded answer after the upstream
 * response is released and serves the same application-visible record data later.
 */
class DnsRecordSnapshotTest {

    @Test
    void cacheSnapshotSurvivesReleaseOfUpstreamResponse() {
        DnsResponse upstream = new DefaultDnsResponse(0x5D42)
                .setRecursionAvailable(true)
                .setCode(DnsResponseCode.NOERROR)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("cache.example.test", DnsRecordType.A))
                .addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(
                        "cache.example.test", DnsRecordType.A, 180,
                        Unpooled.wrappedBuffer(new byte[] {(byte) 192, 0, 2, 101})));
        DnsRawRecord cachedAnswer = null;

        try {
            DnsRawRecord upstreamAnswer = upstream.recordAt(DnsSection.ANSWER);
            cachedAnswer = upstreamAnswer.copy();
        } finally {
            upstream.release();
        }

        try {
            assertThat(cachedAnswer.name()).isEqualTo("cache.example.test.");
            assertThat(cachedAnswer.type()).isEqualTo(DnsRecordType.A);
            assertThat(cachedAnswer.timeToLive()).isEqualTo(180);
            assertThat(cachedAnswer.content().readableBytes()).isEqualTo(4);
            assertThat(cachedAnswer.content().readUnsignedByte()).isEqualTo((short) 192);
            assertThat(cachedAnswer.content().readUnsignedByte()).isEqualTo((short) 0);
            assertThat(cachedAnswer.content().readUnsignedByte()).isEqualTo((short) 2);
            assertThat(cachedAnswer.content().readUnsignedByte()).isEqualTo((short) 101);
        } finally {
            cachedAnswer.release();
        }
    }
}
