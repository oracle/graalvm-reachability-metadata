/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk15on;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.X509CRLEntryHolder;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class X509CRLHolderTest {

    @Test
    void serializationPreservesSignedRevocationsAndExtensions() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair issuerKey = generator.generateKeyPair();
        X500Name issuer = new X500Name("CN=Revocation Authority");
        Date thisUpdate = new Date(1704153600000L);
        Date nextUpdate = new Date(1704758400000L);
        Date revocationDate = new Date(1704067200000L);
        BigInteger revokedSerial = BigInteger.valueOf(123);
        BigInteger crlNumber = BigInteger.valueOf(7);

        X509v2CRLBuilder builder = new X509v2CRLBuilder(issuer, thisUpdate);
        builder.setNextUpdate(nextUpdate);
        builder.addCRLEntry(revokedSerial, revocationDate, CRLReason.keyCompromise);
        builder.addExtension(Extension.cRLNumber, false, new CRLNumber(crlNumber));
        X509CRLHolder original = builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey.getPrivate()));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        X509CRLHolder restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (X509CRLHolder) input.readObject();
        }

        assertThat(restored).isNotSameAs(original).isEqualTo(original);
        assertThat(restored.getEncoded()).isEqualTo(original.getEncoded());
        assertThat(restored.getIssuer()).isEqualTo(issuer);
        assertThat(restored.getThisUpdate()).isEqualTo(thisUpdate);
        assertThat(restored.getNextUpdate()).isEqualTo(nextUpdate);
        assertThat(restored.hasExtensions()).isTrue();
        assertThat(CRLNumber.getInstance(restored.getExtension(Extension.cRLNumber).getParsedValue()).getCRLNumber())
                .isEqualTo(crlNumber);
        assertThat(restored.getRevokedCertificates()).hasSize(1);
        X509CRLEntryHolder entry = restored.getRevokedCertificate(revokedSerial);
        assertThat(entry).isNotNull();
        assertThat(entry.getSerialNumber()).isEqualTo(revokedSerial);
        assertThat(entry.getRevocationDate()).isEqualTo(revocationDate);
        assertThat(entry.getCertificateIssuer().getNames()).containsExactly(new GeneralName(issuer));
        assertThat(CRLReason.getInstance(entry.getExtension(Extension.reasonCode).getParsedValue()).getValue())
                .isEqualTo(BigInteger.valueOf(CRLReason.keyCompromise));
        assertThat(restored.getRevokedCertificate(BigInteger.valueOf(999))).isNull();
        assertThat(restored.isSignatureValid(
                new JcaContentVerifierProviderBuilder().build(issuerKey.getPublic()))).isTrue();
    }
}
