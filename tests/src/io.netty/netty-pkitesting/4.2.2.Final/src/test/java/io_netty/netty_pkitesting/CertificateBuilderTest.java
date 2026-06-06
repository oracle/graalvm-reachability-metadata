/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_pkitesting;

import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CertificateBuilderTest {
    @Test
    void buildIssuedByReadsMlDsaParameterNameFromIssuerPublicKey() throws Exception {
        X509Bundle issuerBundle = new CertificateBuilder()
                .algorithm(CertificateBuilder.Algorithm.mlDsa44)
                .subject("CN=issuer")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();

        X509Bundle leafBundle = new CertificateBuilder()
                .subject("CN=leaf")
                .buildIssuedBy(issuerBundle);

        assertEquals("ML-DSA-44", leafBundle.getCertificate().getSigAlgName());
        assertEquals(
                issuerBundle.getCertificate().getSubjectX500Principal(),
                leafBundle.getCertificate().getIssuerX500Principal());
        assertEquals(2, leafBundle.getCertificatePathWithRoot().length);
        assertNotNull(leafBundle.getKeyPair().getPrivate());
    }
}
