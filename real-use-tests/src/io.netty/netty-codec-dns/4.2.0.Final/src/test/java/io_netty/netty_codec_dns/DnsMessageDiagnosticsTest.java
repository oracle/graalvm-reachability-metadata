/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DNS message diagnostics subsystem: a resolver logs query and response summaries
 * so an operator can identify the request and its outcome without decoding wire data.
 */
class DnsMessageDiagnosticsTest {

    @Test
    void resolverTraceIdentifiesQueryAndResponseOutcome() {
        DnsQuery query = new DefaultDnsQuery(0x1234)
                .setRecursionDesired(true)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("api.example.test", DnsRecordType.A));
        DnsResponse response = new DefaultDnsResponse(query.id())
                .setRecursionAvailable(true)
                .setCode(DnsResponseCode.NXDOMAIN)
                .addRecord(DnsSection.QUESTION,
                        new DefaultDnsQuestion("api.example.test", DnsRecordType.A));

        try {
            String queryLog = query.toString();
            String responseLog = response.toString();

            assertThat(queryLog)
                    .contains("DefaultDnsQuery")
                    .contains("id: 4660")
                    .contains("RD")
                    .contains("api.example.test. IN A");
            assertThat(responseLog)
                    .contains("DefaultDnsResponse")
                    .contains("id: 4660")
                    .contains("NXDomain(3)")
                    .contains("RA")
                    .contains("api.example.test. IN A");
        } finally {
            response.release();
            query.release();
        }
    }
}
