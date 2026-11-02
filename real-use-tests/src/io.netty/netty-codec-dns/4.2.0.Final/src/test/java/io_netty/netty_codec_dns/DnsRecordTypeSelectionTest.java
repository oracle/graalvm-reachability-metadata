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
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DNS record-type selection subsystem: a service-discovery client turns configured
 * record-type names into a query and accepts the matching answer from a resolver.
 */
class DnsRecordTypeSelectionTest {

    @Test
    void serviceDiscoveryUsesConfiguredSrvRecordType() {
        DnsRecordType configuredType = DnsRecordType.valueOf("SRV");
        DnsQuery query = new DefaultDnsQuery(0x3941)
                .setRecursionDesired(true)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("_api._tcp.example.test", configuredType));
        DnsResponse response = new DefaultDnsResponse(query.id())
                .setRecursionAvailable(true)
                .setCode(DnsResponseCode.NOERROR)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("_api._tcp.example.test", configuredType))
                .addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(
                        "_api._tcp.example.test", configuredType, 120,
                        Unpooled.wrappedBuffer(new byte[] {
                                0, 10, 0, 20, 3, 'a', 'p', 'i', 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e',
                                4, 't', 'e', 's', 't', 0
                        })));

        try {
            assertThat(configuredType.name()).isEqualTo("SRV");
            assertThat(configuredType.intValue()).isEqualTo(33);
            assertThat(query.recordAt(DnsSection.QUESTION).type()).isEqualTo(configuredType);
            assertThat(response.code()).isEqualTo(DnsResponseCode.NOERROR);
            assertThat(response.count(DnsSection.ANSWER)).isOne();

            DefaultDnsRawRecord answer = response.recordAt(DnsSection.ANSWER);
            assertThat(answer.type()).isEqualTo(DnsRecordType.SRV);
            assertThat(answer.name()).isEqualTo("_api._tcp.example.test.");
            assertThat(answer.timeToLive()).isEqualTo(120);
            assertThat(answer.content().readUnsignedShort()).isEqualTo(10);
            assertThat(answer.content().readUnsignedShort()).isEqualTo(20);
            assertThat(answer.content().readUnsignedByte()).isEqualTo((short) 3);
        } finally {
            response.release();
            query.release();
        }
    }
}
