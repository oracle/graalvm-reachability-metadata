/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.handler.codec.dns.DefaultDnsOptEcsRecord;
import io.netty.handler.codec.dns.DefaultDnsPtrRecord;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOptEcsRecord;
import io.netty.handler.codec.dns.DnsPtrRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DNS record-model subsystem: a reverse-DNS client reads PTR and EDNS client-subnet
 * records from an authoritative response.
 */
public class DnsRecordModelTest {

    @Test
    void reverseLookupResponseExposesPtrAndClientSubnetRecords() {
        DnsResponse response = new DefaultDnsResponse(0x6B2A)
                .setAuthoritativeAnswer(true)
                .setRecursionAvailable(true)
                .setCode(DnsResponseCode.NOERROR)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("10.2.0.192.in-addr.arpa", DnsRecordType.PTR))
                .addRecord(DnsSection.AUTHORITY, new DefaultDnsPtrRecord(
                        "10.2.0.192.in-addr.arpa", DnsRecord.CLASS_IN, 300, "cache.example.test"))
                .addRecord(DnsSection.ADDITIONAL, new DefaultDnsOptEcsRecord(
                        1232, 24, new byte[] {(byte) 192, 0, 2, 10}));

        try {
            assertThat(response.id()).isEqualTo(0x6B2A);
            assertThat(response.isAuthoritativeAnswer()).isTrue();
            assertThat(response.code()).isEqualTo(DnsResponseCode.NOERROR);
            assertThat(response.count(DnsSection.QUESTION)).isOne();
            assertThat(response.count(DnsSection.AUTHORITY)).isOne();
            assertThat(response.count(DnsSection.ADDITIONAL)).isOne();

            DnsPtrRecord ptr = response.recordAt(DnsSection.AUTHORITY);
            assertThat(ptr.name()).isEqualTo("10.2.0.192.in-addr.arpa.");
            assertThat(ptr.hostname()).isEqualTo("cache.example.test");
            assertThat(ptr.timeToLive()).isEqualTo(300);

            DnsOptEcsRecord clientSubnet = response.recordAt(DnsSection.ADDITIONAL);
            assertThat(clientSubnet.sourcePrefixLength()).isEqualTo(24);
            assertThat(clientSubnet.scopePrefixLength()).isZero();
            assertThat(clientSubnet.address()).containsExactly((byte) 192, 0, 2, 10);
        } finally {
            response.release();
        }
    }
}
