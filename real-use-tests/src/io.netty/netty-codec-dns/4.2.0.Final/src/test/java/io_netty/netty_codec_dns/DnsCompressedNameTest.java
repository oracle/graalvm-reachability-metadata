/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsRecordDecoder;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecordDecoder;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the DNS name-compression subsystem: a gateway decodes a compressed alias response and
 * extracts the canonical hostname from the returned record.
 */
class DnsCompressedNameTest {

    @Test
    void gatewayDecodesCompressedAliasAndCanonicalName() throws Exception {
        ByteBuf wire = Unpooled.buffer();
        writeName(wire, "alias.example.test");
        wire.writeShort(DnsRecordType.CNAME.intValue());
        wire.writeShort(1);
        wire.writeShort(0xC000);
        wire.writeShort(DnsRecordType.CNAME.intValue());
        wire.writeShort(1);
        wire.writeInt(60);
        ByteBuf canonicalName = Unpooled.buffer();
        writeName(canonicalName, "service.example.test");
        wire.writeShort(canonicalName.readableBytes());
        wire.writeBytes(canonicalName);
        canonicalName.release();

        try {
            DnsQuestion question = DnsRecordDecoder.DEFAULT.decodeQuestion(wire);
            DnsRawRecord answer = DnsRecordDecoder.DEFAULT.decodeRecord(wire);
            try {
                assertThat(question.name()).isEqualTo("alias.example.test.");
                assertThat(question.type()).isEqualTo(DnsRecordType.CNAME);
                assertThat(answer.name()).isEqualTo("alias.example.test.");
                assertThat(answer.type()).isEqualTo(DnsRecordType.CNAME);
                assertThat(answer.timeToLive()).isEqualTo(60);
                assertThat(DefaultDnsRecordDecoder.decodeName(answer.content()))
                        .isEqualTo("service.example.test.");
                assertThat(wire.isReadable()).isFalse();
            } finally {
                ReferenceCountUtil.release(answer);
            }
        } finally {
            wire.release();
        }
    }

    private static void writeName(ByteBuf buffer, String name) {
        for (String label : name.split("\\.")) {
            buffer.writeByte(label.length());
            buffer.writeCharSequence(label, StandardCharsets.US_ASCII);
        }
        buffer.writeByte(0);
    }
}
