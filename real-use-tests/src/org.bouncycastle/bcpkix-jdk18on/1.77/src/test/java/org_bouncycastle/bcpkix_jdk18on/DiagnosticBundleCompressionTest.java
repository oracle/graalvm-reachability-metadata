/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.cms.CMSCompressedData;
import org.bouncycastle.cms.CMSCompressedDataGenerator;
import org.bouncycastle.cms.CMSCompressedDataParser;
import org.bouncycastle.cms.CMSCompressedDataStreamGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSTypedStream;
import org.bouncycastle.cms.jcajce.ZlibCompressor;
import org.bouncycastle.cms.jcajce.ZlibExpanderProvider;
import org.bouncycastle.util.io.StreamOverflowException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compresses and expands content with CMS CompressedData from the bcpkix compressed-data
 * generator, stream generator, parser, and the zlib compressor and expander provider.
 *
 * A fleet agent ships diagnostic bundles to a support portal. A small crash report is wrapped
 * in one shot, while the rolling service log is streamed through the compressed-data stream
 * generator in chunks and tagged with the portal's own content type. The portal unpacks bundles
 * with an expansion limit so that a hostile bundle that inflates beyond its budget is rejected
 * instead of exhausting memory.
 */
class DiagnosticBundleCompressionTest {

    private static final ASN1ObjectIdentifier SERVICE_LOG = new ASN1ObjectIdentifier("1.3.6.1.4.1.55555.7.2");

    private static final byte[] CRASH_REPORT = """
            service=inventory-sync host=edge-node-17 build=4.12.3
            signal=SIGSEGV thread=worker-3 uptime=86412s
            frame 0: inventory::Reconciler::merge(Batch const&) +0x1c4
            frame 1: inventory::Reconciler::run() +0x88
            frame 2: std::thread::_State_impl::_M_run() +0x22
            """.getBytes(StandardCharsets.UTF_8);

    @Test
    void crashReportRoundTripsThroughOneShotCompressedData() throws Exception {
        CMSCompressedData bundle = new CMSCompressedDataGenerator()
                .generate(new CMSProcessableByteArray(CRASH_REPORT), new ZlibCompressor());
        byte[] encoded = bundle.getEncoded();

        CMSCompressedData received = new CMSCompressedData(encoded);
        assertThat(received.getContentType()).isEqualTo(CMSObjectIdentifiers.compressedData);
        assertThat(received.getCompressedContentType()).isEqualTo(CMSObjectIdentifiers.data);
        assertThat(received.toASN1Structure().getContentType()).isEqualTo(CMSObjectIdentifiers.compressedData);
        assertThat(received.getContent(new ZlibExpanderProvider())).isEqualTo(CRASH_REPORT);

        ContentInfo reparsed = ContentInfo.getInstance(encoded);
        assertThat(new CMSCompressedData(reparsed).getContent(new ZlibExpanderProvider())).isEqualTo(CRASH_REPORT);
    }

    @Test
    void rollingServiceLogIsStreamedWithPortalContentType() throws Exception {
        byte[] serviceLog = serviceLog(4000);

        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        CMSCompressedDataStreamGenerator generator = new CMSCompressedDataStreamGenerator();
        generator.setBufferSize(512);
        try (OutputStream bundle = generator.open(SERVICE_LOG, wire, new ZlibCompressor())) {
            for (int offset = 0; offset < serviceLog.length; offset += 1500) {
                bundle.write(serviceLog, offset, Math.min(1500, serviceLog.length - offset));
            }
        }
        byte[] encoded = wire.toByteArray();
        assertThat(encoded.length).isLessThan(serviceLog.length / 10);

        CMSCompressedDataParser parser = new CMSCompressedDataParser(encoded);
        CMSTypedStream content = parser.getContent(new ZlibExpanderProvider());
        assertThat(content.getContentType()).isEqualTo(SERVICE_LOG);
        try (InputStream expanded = content.getContentStream()) {
            assertThat(expanded.readAllBytes()).isEqualTo(serviceLog);
        }
        parser.close();

        CMSCompressedData buffered = new CMSCompressedData(encoded);
        assertThat(buffered.getContentType()).isEqualTo(CMSObjectIdentifiers.compressedData);
        assertThat(buffered.getCompressedContentType()).isEqualTo(SERVICE_LOG);
        assertThat(buffered.getContent(new ZlibExpanderProvider())).isEqualTo(serviceLog);
    }

    @Test
    void portalRejectsBundleThatExpandsBeyondItsBudget() throws Exception {
        byte[] serviceLog = serviceLog(2000);
        byte[] encoded = new CMSCompressedDataGenerator()
                .generate(new CMSProcessableByteArray(serviceLog), new ZlibCompressor())
                .getEncoded();

        assertThat(new CMSCompressedData(encoded).getContent(new ZlibExpanderProvider(serviceLog.length)))
                .isEqualTo(serviceLog);

        long budget = serviceLog.length / 4;
        assertThatThrownBy(() -> new CMSCompressedData(encoded).getContent(new ZlibExpanderProvider(budget)))
                .isInstanceOf(CMSException.class)
                .hasCauseInstanceOf(StreamOverflowException.class);

        CMSCompressedDataParser parser = new CMSCompressedDataParser(encoded);
        try (InputStream expanded = parser.getContent(new ZlibExpanderProvider(budget)).getContentStream()) {
            assertThatThrownBy(expanded::readAllBytes)
                    .isInstanceOf(StreamOverflowException.class)
                    .isInstanceOf(IOException.class);
        }
        parser.close();
    }

    private static byte[] serviceLog(int lines) {
        StringBuilder log = new StringBuilder();
        for (int line = 0; line < lines; line++) {
            log.append("2026-10-18T03:")
                    .append(String.format("%02d:%02d", (line / 60) % 60, line % 60))
                    .append("Z INFO  inventory-sync reconciled batch=")
                    .append(line)
                    .append(" items=250 status=OK\n");
        }
        return log.toString().getBytes(StandardCharsets.UTF_8);
    }
}
