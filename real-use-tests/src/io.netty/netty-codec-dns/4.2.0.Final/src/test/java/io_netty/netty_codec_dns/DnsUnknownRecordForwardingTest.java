/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsRecordDecoder;
import io.netty.handler.codec.dns.DnsRecordEncoder;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsRawRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the unknown-record forwarding subsystem: a gateway relays a DNS record type it does
 * not interpret while preserving the wire value and opaque application payload.
 */
class DnsUnknownRecordForwardingTest {

    @Test
    void gatewayForwardsUnknownRecordWithoutInterpretingPayload() throws Exception {
        DnsRecordType experimentalType = DnsRecordType.valueOf(65400);
        DnsRawRecord record = new DefaultDnsRawRecord(
                "service.example.test", experimentalType, 180,
                Unpooled.wrappedBuffer(new byte[] {0x01, 0x23, (byte) 0xFE, 0x45}));
        ByteBuf wire = Unpooled.buffer();

        try {
            DnsRecordEncoder.DEFAULT.encodeRecord(record, wire);
            DnsRawRecord forwarded = DnsRecordDecoder.DEFAULT.decodeRecord(wire);
            try {
                assertThat(forwarded.name()).isEqualTo("service.example.test.");
                assertThat(forwarded.type().intValue()).isEqualTo(65400);
                assertThat(forwarded.type().name()).isEqualTo("UNKNOWN");
                assertThat(forwarded.timeToLive()).isEqualTo(180);
                assertThat(forwarded.content().readableBytes()).isEqualTo(4);
                assertThat(forwarded.content().readUnsignedByte()).isEqualTo((short) 1);
                assertThat(forwarded.content().readUnsignedByte()).isEqualTo((short) 0x23);
                assertThat(forwarded.content().readUnsignedByte()).isEqualTo((short) 0xFE);
                assertThat(forwarded.content().readUnsignedByte()).isEqualTo((short) 0x45);
                assertThat(wire.isReadable()).isFalse();
            } finally {
                forwarded.release();
            }
        } finally {
            record.release();
            wire.release();
        }
    }
}
