/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.x509.X509CertStoreSelector;
import org.bouncycastle.x509.X509CollectionStoreParameters;
import org.bouncycastle.x509.X509Store;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class X509UtilTest {
    @Test
    void loadsCollectionStoreFromProvider() throws Exception {
        KeyPair pair = KeyPairFixtures.classical("EC");
        X509Certificate certificate = selfSigned(pair, "CN=Store Test");
        certificate.verify(pair.getPublic(), KeyPairFixtures.BC);
        List<X509Certificate> entries = List.of(certificate);
        X509Store store = X509Store.getInstance("CERTIFICATE/COLLECTION",
                new X509CollectionStoreParameters(entries), KeyPairFixtures.BC);
        X509CertStoreSelector selector = new X509CertStoreSelector();
        selector.setSerialNumber(BigInteger.ONE);
        assertEquals(entries, List.copyOf(store.getMatches(selector)));
        selector.setSerialNumber(BigInteger.TWO);
        assertEquals(List.of(), List.copyOf(store.getMatches(selector)));
        assertSame(KeyPairFixtures.BC, store.getProvider());
    }

    @Test
    void writesCertificateThatTheJdkFactoryReads() throws Exception {
        // Native Image 25.0 cannot build an image that registers classes of the signed bcprov jar
        // unless the image parses X.509 certificates itself, so this test keeps the JDK parser
        // reachable: https://github.com/oracle/graal/issues/14806 (GR-80933)
        X509Certificate certificate = selfSigned(KeyPairFixtures.classical("EC"), "CN=Interop Test");
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate parsed = (X509Certificate) factory.generateCertificate(
                new ByteArrayInputStream(certificate.getEncoded()));
        assertArrayEquals(certificate.getEncoded(), parsed.getEncoded());
        assertEquals(certificate.getSubjectX500Principal(), parsed.getSubjectX500Principal());
    }

    private static X509Certificate selfSigned(KeyPair pair, String name) throws Exception {
        X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
        X500Principal principal = new X500Principal(name);
        generator.setSerialNumber(BigInteger.ONE);
        generator.setIssuerDN(principal);
        generator.setSubjectDN(principal);
        generator.setNotBefore(new Date(0));
        generator.setNotAfter(new Date(4102444800000L));
        generator.setPublicKey(pair.getPublic());
        generator.setSignatureAlgorithm("SHA256withECDSA");
        return generator.generate(pair.getPrivate());
    }
}
