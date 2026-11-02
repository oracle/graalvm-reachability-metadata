/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DNS zero-copy forwarding subsystem: a proxy retains a record view while the
 * upstream response is released and forwards the original payload without copying it.
 */
class DnsZeroCopyForwardingTest {

    @Test
    void proxyRetainsRecordViewAfterUpstreamRelease() {
        DefaultDnsRawRecord upstreamRecord = new DefaultDnsRawRecord(
                "service.example.test", DnsRecordType.A, 45,
                Unpooled.wrappedBuffer(new byte[] {(byte) 192, 0, 2, (byte) 203}));
        DnsRawRecord forwardedRecord = upstreamRecord.retainedDuplicate();
        DnsRawRecord rewrittenRecord = null;

        try {
            assertThat(forwardedRecord.name()).isEqualTo("service.example.test.");
            assertThat(forwardedRecord.type()).isEqualTo(DnsRecordType.A);
            assertThat(forwardedRecord.timeToLive()).isEqualTo(45);
            assertThat(forwardedRecord.content().readableBytes()).isEqualTo(4);
            assertThat(forwardedRecord.content().getUnsignedByte(3)).isEqualTo((short) 203);

            assertThat(upstreamRecord.release()).isFalse();

            assertThat(forwardedRecord.refCnt()).isOne();
            assertThat(forwardedRecord.content().getUnsignedByte(0)).isEqualTo((short) 192);
            assertThat(forwardedRecord.content().getUnsignedByte(1)).isZero();
            assertThat(forwardedRecord.content().getUnsignedByte(2)).isEqualTo((short) 2);
            assertThat(forwardedRecord.content().getUnsignedByte(3)).isEqualTo((short) 203);

            rewrittenRecord = forwardedRecord.replace(
                    Unpooled.wrappedBuffer(new byte[] {(byte) 198, 51, 100, 14}));
            assertThat(rewrittenRecord.name()).isEqualTo("service.example.test.");
            assertThat(rewrittenRecord.type()).isEqualTo(DnsRecordType.A);
            assertThat(rewrittenRecord.timeToLive()).isEqualTo(45);
            assertThat(rewrittenRecord.content().readableBytes()).isEqualTo(4);
            assertThat(rewrittenRecord.content().getUnsignedByte(0)).isEqualTo((short) 198);
            assertThat(rewrittenRecord.content().getUnsignedByte(1)).isEqualTo((short) 51);
            assertThat(rewrittenRecord.content().getUnsignedByte(2)).isEqualTo((short) 100);
            assertThat(rewrittenRecord.content().getUnsignedByte(3)).isEqualTo((short) 14);
        } finally {
            if (rewrittenRecord != null) {
                rewrittenRecord.release();
            }
            forwardedRecord.release();
        }
    }
}
