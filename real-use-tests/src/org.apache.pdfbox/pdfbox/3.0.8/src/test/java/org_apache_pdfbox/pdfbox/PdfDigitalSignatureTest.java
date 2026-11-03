/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises PDFBox's digital signature subsystem to sign and verify a customer approval PDF.
public class PdfDigitalSignatureTest {
    @Test
    @Timeout(60)
    public void signsAndReloadsApprovedReport() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPair keyPair = createKeyPair();
        X509Certificate certificate = createCertificate(keyPair);
        byte[] unsignedReport;
        byte[] reportPdf;
        Calendar signingDate = fixedDate();

        try (PDDocument report = new PDDocument()) {
            report.addPage(new PDPage());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            report.save(output);
            unsignedReport = output.toByteArray();
        }

        try (PDDocument report = Loader.loadPDF(unsignedReport)) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName("Finance Operations");
            signature.setReason("Approved for distribution");
            signature.setLocation("London");
            signature.setSignDate(signingDate);
            report.addSignature(signature, new CmsSignature(keyPair, certificate));

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(unsignedReport);
            report.saveIncremental(output);
            reportPdf = output.toByteArray();
        }

        try (PDDocument signedReport = Loader.loadPDF(reportPdf)) {
            assertEquals(1, signedReport.getSignatureDictionaries().size());
            PDSignature signature = signedReport.getLastSignatureDictionary();
            assertNotNull(signature);
            assertEquals("Finance Operations", signature.getName());
            assertEquals("Approved for distribution", signature.getReason());
            assertEquals("London", signature.getLocation());
            assertEquals(signingDate.getTime(), signature.getSignDate().getTime());
            byte[] contents = signature.getContents();
            assertTrue(contents.length > 0);
            assertFalse(signature.getSignedContent(reportPdf).length == 0);
            assertTrue(new CMSSignedData(contents).getCertificates().getMatches(null).size() > 0);
        }
    }

    private static KeyPair createKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate createCertificate(KeyPair keyPair) throws Exception {
        X500Name subject = new X500Name("CN=PDFBox real-use signer");
        Date notBefore = new Date(1704067200000L);
        Date notAfter = new Date(1735689600000L);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.ONE, notBefore, notAfter, subject, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC")
                .build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
    }

    private static Calendar fixedDate() {
        GregorianCalendar date = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        date.clear();
        date.set(2024, Calendar.JANUARY, 15, 10, 30, 0);
        return date;
    }

    private static final class CmsSignature implements SignatureInterface {
        private final KeyPair keyPair;
        private final X509Certificate certificate;

        private CmsSignature(KeyPair keyPair, X509Certificate certificate) {
            this.keyPair = keyPair;
            this.certificate = certificate;
        }

        @Override
        public byte[] sign(InputStream content) throws IOException {
            try {
                CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
                ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider("BC")
                        .build(keyPair.getPrivate());
                generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                        new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                        .build(signer, certificate));
                generator.addCertificate(new X509CertificateHolder(certificate.getEncoded()));
                byte[] bytes = content.readAllBytes();
                CMSSignedData signedData = generator.generate(new CMSProcessableByteArray(bytes), false);
                return signedData.getEncoded();
            } catch (Exception exception) {
                throw new IOException("Could not create CMS signature", exception);
            }
        }
    }
}
