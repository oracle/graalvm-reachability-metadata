/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_dns;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder;
import io.netty.handler.codec.dns.DatagramDnsQueryEncoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import io.netty.handler.codec.dns.DefaultDnsOptEcsRecord;
import io.netty.handler.codec.dns.DefaultDnsPtrRecord;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsRecordDecoder;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsOptEcsRecord;
import io.netty.handler.codec.dns.DnsPtrRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordDecoder;
import io.netty.handler.codec.dns.DnsRecordEncoder;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsResponseDecoder;
import io.netty.handler.codec.dns.TcpDnsResponseEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Netty_codec_dnsTest {
    private static final InetSocketAddress CLIENT = new InetSocketAddress("192.0.2.10", 53000);
    private static final InetSocketAddress SERVER = new InetSocketAddress("198.51.100.53", 53);

    @Test
    void dnsMessagesManageSectionsFlagsAndReferenceCountedRecords() {
        DnsQuestion question = new DefaultDnsQuestion("www.example.com.", DnsRecordType.AAAA);
        DnsRawRecord answer = ipv4Record("www.example.com.", 60, 203, 0, 113, 7);
        DnsPtrRecord authority = new DefaultDnsPtrRecord(
                "7.113.0.203.in-addr.arpa.", DnsRecord.CLASS_IN, 120, "www.example.com.");
        DnsOptEcsRecord additional = new DefaultDnsOptEcsRecord(
                4096, 24, new byte[] {(byte) 203, 0, 113, 99});
        DefaultDnsResponse response = new DefaultDnsResponse(0xCAFE, DnsOpCode.QUERY, DnsResponseCode.NOERROR);

        try {
            response.setAuthoritativeAnswer(true)
                    .setRecursionDesired(true)
                    .setRecursionAvailable(true)
                    .setZ(3)
                    .addRecord(DnsSection.QUESTION, question)
                    .addRecord(DnsSection.ANSWER, answer)
                    .addRecord(DnsSection.AUTHORITY, authority)
                    .addRecord(DnsSection.ADDITIONAL, additional);

            assertThat(response.id()).isEqualTo(0xCAFE);
            assertThat(response.opCode()).isSameAs(DnsOpCode.QUERY);
            assertThat(response.code()).isSameAs(DnsResponseCode.NOERROR);
            assertThat(response.isAuthoritativeAnswer()).isTrue();
            assertThat(response.isRecursionDesired()).isTrue();
            assertThat(response.isRecursionAvailable()).isTrue();
            assertThat(response.z()).isEqualTo(3);
            assertThat(response.count()).isEqualTo(4);
            assertThat(response.count(DnsSection.QUESTION)).isOne();
            assertThat(response.<DnsQuestion>recordAt(DnsSection.QUESTION)).isSameAs(question);
            assertThat(response.<DnsRawRecord>recordAt(DnsSection.ANSWER)).isSameAs(answer);
            assertThat(response.<DnsPtrRecord>recordAt(DnsSection.AUTHORITY)).isSameAs(authority);
            assertThat(response.<DnsOptEcsRecord>recordAt(DnsSection.ADDITIONAL)).isSameAs(additional);

            DnsPtrRecord removed = response.removeRecord(DnsSection.AUTHORITY, 0);

            assertThat(removed).isSameAs(authority);
            assertThat(response.count(DnsSection.AUTHORITY)).isZero();
            assertThat(response.count()).isEqualTo(3);
        } finally {
            response.release();
        }

        assertThat(answer.refCnt()).isZero();
    }

    @Test
    void registryTypesCodesAndOpcodesResolveKnownAndUnknownValues() {
        DnsRecordType privateType = DnsRecordType.valueOf(65280);
        DnsResponseCode privateCode = DnsResponseCode.valueOf(3841);
        DnsOpCode privateOpCode = DnsOpCode.valueOf(15);

        assertThat(DnsRecordType.valueOf("A")).isSameAs(DnsRecordType.A);
        assertThat(DnsRecordType.valueOf(DnsRecordType.A.intValue())).isSameAs(DnsRecordType.A);
        assertThat(privateType.intValue()).isEqualTo(65280);
        assertThat(privateType).isEqualTo(new DnsRecordType(65280, "PRIVATE"));
        assertThat(privateType).isGreaterThan(DnsRecordType.A);

        assertThat(DnsResponseCode.valueOf(DnsResponseCode.NXDOMAIN.intValue())).isSameAs(DnsResponseCode.NXDOMAIN);
        assertThat(privateCode.intValue()).isEqualTo(3841);
        assertThat(privateCode).isEqualTo(new DnsResponseCode(3841, "PRIVATE"));
        assertThat(privateCode).isGreaterThan(DnsResponseCode.NOERROR);

        assertThat(DnsOpCode.valueOf(DnsOpCode.UPDATE.byteValue())).isSameAs(DnsOpCode.UPDATE);
        assertThat(privateOpCode.byteValue()).isEqualTo((byte) 15);
        assertThat(privateOpCode).isEqualTo(new DnsOpCode(15, "PRIVATE"));
        assertThat(privateOpCode).isGreaterThan(DnsOpCode.QUERY);
    }

    @Test
    void recordEncoderAndDecoderRoundTripQuestionsRawRecordsAndDecodePtrRecords() throws Exception {
        DnsQuestion question = new DefaultDnsQuestion("api.example.com.", DnsRecordType.AAAA);
        ByteBuf questionBuffer = Unpooled.buffer();
        DnsRawRecord rawRecord = ipv4Record("api.example.com.", 300, 192, 0, 2, 44);
        DnsRawRecord decodedRawRecord = null;
        ByteBuf rawRecordBuffer = Unpooled.buffer();
        ByteBuf ptrRecordBuffer = Unpooled.buffer();

        try {
            DnsRecordEncoder.DEFAULT.encodeQuestion(question, questionBuffer);
            DnsQuestion decodedQuestion = DnsRecordDecoder.DEFAULT.decodeQuestion(questionBuffer);

            assertQuestion(decodedQuestion, "api.example.com.", DnsRecordType.AAAA, DnsRecord.CLASS_IN);

            DnsRecordEncoder.DEFAULT.encodeRecord(rawRecord, rawRecordBuffer);
            decodedRawRecord = DnsRecordDecoder.DEFAULT.decodeRecord(rawRecordBuffer);

            assertThat(decodedRawRecord.name()).isEqualTo("api.example.com.");
            assertThat(decodedRawRecord.type()).isSameAs(DnsRecordType.A);
            assertThat(decodedRawRecord.dnsClass()).isEqualTo(DnsRecord.CLASS_IN);
            assertThat(decodedRawRecord.timeToLive()).isEqualTo(300);
            assertThat(readBytes(decodedRawRecord)).containsExactly((byte) 192, (byte) 0, (byte) 2, (byte) 44);

            writePtrRecord(ptrRecordBuffer, "44.2.0.192.in-addr.arpa.", 600, "api.example.com.");
            DnsPtrRecord decodedPtrRecord = DnsRecordDecoder.DEFAULT.decodeRecord(ptrRecordBuffer);

            assertThat(decodedPtrRecord.name()).isEqualTo("44.2.0.192.in-addr.arpa.");
            assertThat(decodedPtrRecord.type()).isSameAs(DnsRecordType.PTR);
            assertThat(decodedPtrRecord.timeToLive()).isEqualTo(600);
            assertThat(decodedPtrRecord.hostname()).isEqualTo("api.example.com.");
        } finally {
            questionBuffer.release();
            rawRecord.release();
            if (decodedRawRecord != null) {
                decodedRawRecord.release();
            }
            rawRecordBuffer.release();
            ptrRecordBuffer.release();
        }
    }

    @Test
    void recordDecoderExpandsCompressedRecordNamesAndCanonicalNameData() throws Exception {
        ByteBuf packet = Unpooled.buffer();
        DnsRawRecord decodedCnameRecord = null;

        try {
            int canonicalNameOffset = packet.writerIndex();
            writeName(packet, "canonical.example.com.");
            int sharedSuffixOffset = canonicalNameOffset + 1 + "canonical".length();
            packet.writeShort(DnsRecordType.A.intValue());
            packet.writeShort(DnsRecord.CLASS_IN);

            packet.writeByte("alias".length());
            packet.writeCharSequence("alias", StandardCharsets.US_ASCII);
            packet.writeShort(0xC000 | sharedSuffixOffset);
            packet.writeShort(DnsRecordType.CNAME.intValue());
            packet.writeShort(DnsRecord.CLASS_IN);
            packet.writeInt(180);
            packet.writeShort(Short.BYTES);
            packet.writeShort(0xC000 | canonicalNameOffset);

            DnsRecordDecoder.DEFAULT.decodeQuestion(packet);
            decodedCnameRecord = DnsRecordDecoder.DEFAULT.decodeRecord(packet);

            assertThat(decodedCnameRecord.name()).isEqualTo("alias.example.com.");
            assertThat(decodedCnameRecord.type()).isSameAs(DnsRecordType.CNAME);
            assertThat(decodedCnameRecord.dnsClass()).isEqualTo(DnsRecord.CLASS_IN);
            assertThat(decodedCnameRecord.timeToLive()).isEqualTo(180);

            ByteBuf canonicalNameContent = decodedCnameRecord.content().duplicate();
            assertThat(DefaultDnsRecordDecoder.decodeName(canonicalNameContent)).isEqualTo("canonical.example.com.");
            assertThat(canonicalNameContent.isReadable()).isFalse();
        } finally {
            if (decodedCnameRecord != null) {
                decodedCnameRecord.release();
            }
            packet.release();
        }
    }

    @Test
    void datagramQueryEncoderAndDecoderRoundTripEnvelopeHeaderAndSections() {
        DatagramDnsQuery query = new DatagramDnsQuery(CLIENT, SERVER, 0xBEEF)
                .setRecursionDesired(true)
                .addRecord(DnsSection.QUESTION, new DefaultDnsQuestion("service.example.com.", DnsRecordType.SRV))
                .addRecord(DnsSection.ADDITIONAL, new DefaultDnsOptEcsRecord(
                        1232, InternetProtocolFamily.IPv4));
        EmbeddedChannel encoder = new EmbeddedChannel(new DatagramDnsQueryEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(new DatagramDnsQueryDecoder());
        DatagramDnsQuery decoded = null;

        try {
            assertThat(encoder.writeOutbound(query)).isTrue();
            DatagramPacket packet = encoder.readOutbound();

            assertThat(packet.sender()).isNull();
            assertThat(packet.recipient()).isEqualTo(SERVER);
            assertThat(packet.content().readableBytes()).isGreaterThan(12);

            assertThat(decoder.writeInbound(packet)).isTrue();
            decoded = decoder.readInbound();

            assertThat(decoded.sender()).isNull();
            assertThat(decoded.recipient()).isEqualTo(SERVER);
            assertThat(decoded.id()).isEqualTo(0xBEEF);
            assertThat(decoded.isRecursionDesired()).isTrue();
            assertThat(decoded.count(DnsSection.QUESTION)).isOne();
            assertThat(decoded.count(DnsSection.ADDITIONAL)).isOne();
            assertQuestion(decoded.recordAt(DnsSection.QUESTION),
                    "service.example.com.", DnsRecordType.SRV, DnsRecord.CLASS_IN);
            assertThat(decoded.<DnsRecord>recordAt(DnsSection.ADDITIONAL).type()).isSameAs(DnsRecordType.OPT);
        } finally {
            if (decoded != null) {
                decoded.release();
            }
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

    @Test
    void datagramResponseEncoderAndDecoderRoundTripEnvelopeResponseCodeAndTruncatedFlag() {
        DatagramDnsResponse response = new DatagramDnsResponse(
                SERVER, CLIENT, 0xD00D, DnsOpCode.QUERY, DnsResponseCode.NXDOMAIN)
                .setAuthoritativeAnswer(true)
                .setTruncated(true)
                .addRecord(DnsSection.QUESTION, new DefaultDnsQuestion("missing.example.com.", DnsRecordType.A));
        EmbeddedChannel encoder = new EmbeddedChannel(new DatagramDnsResponseEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(new DatagramDnsResponseDecoder());
        DatagramDnsResponse decoded = null;

        try {
            assertThat(encoder.writeOutbound(response)).isTrue();
            DatagramPacket packet = encoder.readOutbound();

            assertThat(packet.sender()).isNull();
            assertThat(packet.recipient()).isEqualTo(CLIENT);
            assertThat(packet.content().readableBytes()).isGreaterThan(12);

            assertThat(decoder.writeInbound(packet)).isTrue();
            decoded = decoder.readInbound();

            assertThat(decoded.sender()).isNull();
            assertThat(decoded.recipient()).isEqualTo(CLIENT);
            assertThat(decoded.id()).isEqualTo(0xD00D);
            assertThat(decoded.code()).isSameAs(DnsResponseCode.NXDOMAIN);
            assertThat(decoded.isAuthoritativeAnswer()).isTrue();
            assertThat(decoded.isTruncated()).isTrue();
            assertThat(decoded.count(DnsSection.QUESTION)).isOne();
            assertThat(decoded.count(DnsSection.ANSWER)).isZero();
            assertQuestion(decoded.recordAt(DnsSection.QUESTION),
                    "missing.example.com.", DnsRecordType.A, DnsRecord.CLASS_IN);
        } finally {
            if (decoded != null) {
                decoded.release();
            }
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

    @Test
    void tcpResponseEncoderAndDecoderRoundTripLengthPrefixFlagsAndAnswers() {
        DnsResponse response = new DefaultDnsResponse(0x1234, DnsOpCode.QUERY, DnsResponseCode.NOERROR)
                .setAuthoritativeAnswer(true)
                .setRecursionDesired(true)
                .setRecursionAvailable(true)
                .addRecord(DnsSection.QUESTION, new DefaultDnsQuestion("edge.example.com.", DnsRecordType.A))
                .addRecord(DnsSection.ANSWER, ipv4Record("edge.example.com.", 30, 198, 51, 100, 25))
                .addRecord(DnsSection.AUTHORITY, ipv4Record("ns.example.com.", 60, 198, 51, 100, 53));
        EmbeddedChannel encoder = new EmbeddedChannel(new TcpDnsResponseEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(new TcpDnsResponseDecoder());
        DnsResponse decoded = null;

        try {
            assertThat(encoder.writeOutbound(response)).isTrue();
            ByteBuf frame = encoder.readOutbound();

            assertThat(frame.getUnsignedShort(frame.readerIndex())).isEqualTo(frame.readableBytes() - Short.BYTES);
            assertThat(decoder.writeInbound(frame)).isTrue();
            decoded = decoder.readInbound();

            assertThat(decoded.id()).isEqualTo(0x1234);
            assertThat(decoded.code()).isSameAs(DnsResponseCode.NOERROR);
            assertThat(decoded.isAuthoritativeAnswer()).isTrue();
            assertThat(decoded.isRecursionDesired()).isTrue();
            assertThat(decoded.isRecursionAvailable()).isTrue();
            assertQuestion(decoded.recordAt(DnsSection.QUESTION),
                    "edge.example.com.", DnsRecordType.A, DnsRecord.CLASS_IN);
            DnsRawRecord decodedAnswer = decoded.recordAt(DnsSection.ANSWER);
            assertThat(decodedAnswer.timeToLive()).isEqualTo(30);
            assertThat(readBytes(decodedAnswer)).containsExactly((byte) 198, (byte) 51, (byte) 100, (byte) 25);
            DnsRawRecord decodedAuthority = decoded.recordAt(DnsSection.AUTHORITY);
            assertThat(decodedAuthority.name()).isEqualTo("ns.example.com.");
            assertThat(decodedAuthority.timeToLive()).isEqualTo(60);
            assertThat(readBytes(decodedAuthority)).containsExactly((byte) 198, (byte) 51, (byte) 100, (byte) 53);
        } finally {
            if (decoded != null) {
                decoded.release();
            }
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

    private static void writePtrRecord(ByteBuf buffer, String name, long timeToLive, String hostname) {
        ByteBuf targetNameBuffer = Unpooled.buffer();
        try {
            writeName(buffer, name);
            buffer.writeShort(DnsRecordType.PTR.intValue());
            buffer.writeShort(DnsRecord.CLASS_IN);
            buffer.writeInt((int) timeToLive);
            writeName(targetNameBuffer, hostname);
            buffer.writeShort(targetNameBuffer.readableBytes());
            buffer.writeBytes(targetNameBuffer, targetNameBuffer.readerIndex(), targetNameBuffer.readableBytes());
        } finally {
            targetNameBuffer.release();
        }
    }

    private static void writeName(ByteBuf buffer, String name) {
        String normalizedName = name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
        if (normalizedName.isEmpty()) {
            buffer.writeByte(0);
            return;
        }
        for (String label : normalizedName.split("\\.")) {
            buffer.writeByte(label.length());
            buffer.writeCharSequence(label, StandardCharsets.US_ASCII);
        }
        buffer.writeByte(0);
    }

    private static DnsRawRecord ipv4Record(String name, long timeToLive, int first, int second, int third, int fourth) {
        byte[] address = new byte[] {(byte) first, (byte) second, (byte) third, (byte) fourth};
        return new DefaultDnsRawRecord(
                name, DnsRecordType.A, DnsRecord.CLASS_IN, timeToLive, Unpooled.wrappedBuffer(address));
    }

    private static byte[] readBytes(DnsRawRecord record) {
        ByteBuf content = record.content().duplicate();
        byte[] bytes = new byte[content.readableBytes()];
        content.readBytes(bytes);
        return bytes;
    }

    private static void assertQuestion(
            DnsQuestion question, String expectedName, DnsRecordType expectedType, int expectedDnsClass) {
        assertThat(question.name()).isEqualTo(expectedName);
        assertThat(question.type()).isSameAs(expectedType);
        assertThat(question.dnsClass()).isEqualTo(expectedDnsClass);
    }
}
