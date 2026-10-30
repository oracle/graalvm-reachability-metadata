/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.x509.X509CertStoreSelector;
import org.bouncycastle.x509.X509CollectionStoreParameters;
import org.bouncycastle.x509.X509Store;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class X509UtilTest {
    @Test
    void loadsCollectionStoreFromProvider() throws Exception {
        KeyPair pair = KeyPairFixtures.classical("EC");
        X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
        X500Principal principal = new X500Principal("CN=Store Test");
        generator.setSerialNumber(BigInteger.ONE);
        generator.setIssuerDN(principal);
        generator.setSubjectDN(principal);
        generator.setNotBefore(new Date(0));
        generator.setNotAfter(new Date(4102444800000L));
        generator.setPublicKey(pair.getPublic());
        generator.setSignatureAlgorithm("SHA256withECDSA");
        X509Certificate certificate = generator.generate(pair.getPrivate());
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
}
