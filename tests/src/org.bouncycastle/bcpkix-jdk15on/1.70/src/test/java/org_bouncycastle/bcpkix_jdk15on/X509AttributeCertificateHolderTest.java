/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk15on;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.AttributeCertificateHolder;
import org.bouncycastle.cert.AttributeCertificateIssuer;
import org.bouncycastle.cert.X509AttributeCertificateHolder;
import org.bouncycastle.cert.X509v2AttributeCertificateBuilder;
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

public class X509AttributeCertificateHolderTest {

    @Test
    void serializationPreservesSignedAttributesAndExtensions() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair issuerKey = generator.generateKeyPair();
        X500Name holderName = new X500Name("CN=Service Reader");
        X500Name issuerName = new X500Name("CN=Authorization Authority");
        ASN1ObjectIdentifier permissionOid = new ASN1ObjectIdentifier("1.3.6.1.4.1.55555.1");
        Date notBefore = new Date(1704067200000L);
        Date notAfter = new Date(1735689600000L);

        X509v2AttributeCertificateBuilder builder = new X509v2AttributeCertificateBuilder(
                new AttributeCertificateHolder(holderName),
                new AttributeCertificateIssuer(issuerName),
                BigInteger.valueOf(42), notBefore, notAfter);
        builder.addAttribute(permissionOid, new DERUTF8String("read:documents"));
        builder.addExtension(Extension.noRevAvail, false, DERNull.INSTANCE);
        X509AttributeCertificateHolder original = builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey.getPrivate()));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        X509AttributeCertificateHolder restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (X509AttributeCertificateHolder) input.readObject();
        }

        assertThat(restored).isNotSameAs(original).isEqualTo(original);
        assertThat(restored.getEncoded()).isEqualTo(original.getEncoded());
        assertThat(restored.getSerialNumber()).isEqualTo(BigInteger.valueOf(42));
        assertThat(restored.getHolder().getEntityNames()).containsExactly(holderName);
        assertThat(restored.getIssuer().getNames()).containsExactly(issuerName);
        assertThat(restored.getNotBefore()).isEqualTo(notBefore);
        assertThat(restored.getNotAfter()).isEqualTo(notAfter);
        assertThat(restored.isValidOn(new Date(1719792000000L))).isTrue();
        assertThat(restored.getAttributes(permissionOid)).hasSize(1);
        assertThat(restored.getAttributes(permissionOid)[0].getAttrValues().getObjectAt(0))
                .isEqualTo(new DERUTF8String("read:documents"));
        assertThat(restored.hasExtensions()).isTrue();
        assertThat(restored.getExtension(Extension.noRevAvail).getParsedValue()).isEqualTo(DERNull.INSTANCE);
        assertThat(restored.isSignatureValid(
                new JcaContentVerifierProviderBuilder().build(issuerKey.getPublic()))).isTrue();
    }
}
