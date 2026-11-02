/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DNS dynamic-update control subsystem: a service submits an UPDATE operation and
 * interprets the resolver response flags and status before reporting the update result.
 */
class DnsDynamicUpdateControlTest {

    @Test
    void updateClientPreservesOperationAndInterpretsResponseStatus() {
        DnsQuery update = new DefaultDnsQuery(0x7A31, DnsOpCode.UPDATE)
                .setRecursionDesired(false)
                .setZ(5)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("example.test", DnsRecordType.SOA));

        try {
            assertThat(update.id()).isEqualTo(0x7A31);
            assertThat(update.opCode()).isEqualTo(DnsOpCode.UPDATE);
            assertThat(update.isRecursionDesired()).isFalse();
            assertThat(update.z()).isEqualTo(5);
            assertThat(update.count(DnsSection.QUESTION)).isOne();
            assertThat(update.recordAt(DnsSection.QUESTION).name()).isEqualTo("example.test.");
            assertThat(update.recordAt(DnsSection.QUESTION).type()).isEqualTo(DnsRecordType.SOA);

            DnsResponse response = new DefaultDnsResponse(
                    update.id(), update.opCode(), DnsResponseCode.REFUSED)
                    .setAuthoritativeAnswer(true)
                    .setTruncated(false)
                    .setRecursionAvailable(false)
                    .setZ(update.z());
            try {
                assertThat(response.id()).isEqualTo(update.id());
                assertThat(response.opCode()).isEqualTo(DnsOpCode.UPDATE);
                assertThat(response.code()).isEqualTo(DnsResponseCode.REFUSED);
                assertThat(response.isAuthoritativeAnswer()).isTrue();
                assertThat(response.isTruncated()).isFalse();
                assertThat(response.isRecursionAvailable()).isFalse();
                assertThat(response.z()).isEqualTo(5);
            } finally {
                response.release();
            }
        } finally {
            update.release();
        }
    }
}
