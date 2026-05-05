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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CertificateBuilderTest {
    @Test
    void buildIssuedByReadsMlDsaParameterNameFromIssuerPublicKey() {
        PublicKey issuerPublicKey = new MlDsaPublicKey();
        X509Certificate issuerCertificate = new MinimalX509Certificate(issuerPublicKey);
        X509Bundle issuerBundle = X509Bundle.fromCertificatePath(
                new X509Certificate[] {issuerCertificate},
                issuerCertificate,
                new KeyPair(issuerPublicKey, new MinimalPrivateKey()));

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> new CertificateBuilder()
                        .subject("CN=leaf")
                        .publicKey(new MlDsaPublicKey())
                        .buildIssuedBy(issuerBundle));

        assertTrue(exception.getMessage().contains("Algorithm not supported: test-ml-dsa"));
    }

    public static final class MlDsaPublicKey implements PublicKey {
        public MlDsaParameters getParams() {
            return new MlDsaParameters();
        }

        @Override
        public String getAlgorithm() {
            return "ML-DSA";
        }

        @Override
        public String getFormat() {
            return "X.509";
        }

        @Override
        public byte[] getEncoded() {
            return new byte[0];
        }
    }

    public static final class MlDsaParameters {
        public String getName() {
            return "test-ml-dsa";
        }
    }

    private static final class MinimalPrivateKey implements PrivateKey {
        @Override
        public String getAlgorithm() {
            return "ML-DSA";
        }

        @Override
        public String getFormat() {
            return "PKCS#8";
        }

        @Override
        public byte[] getEncoded() {
            return new byte[0];
        }
    }

    private static final class MinimalX509Certificate extends X509Certificate {
        private final PublicKey publicKey;
        private final X500Principal principal = new X500Principal("CN=issuer");

        MinimalX509Certificate(PublicKey publicKey) {
            this.publicKey = publicKey;
        }

        @Override
        public void checkValidity() {
        }

        @Override
        public void checkValidity(Date date) {
        }

        @Override
        public int getVersion() {
            return 3;
        }

        @Override
        public BigInteger getSerialNumber() {
            return BigInteger.ONE;
        }

        @Override
        public Principal getIssuerDN() {
            return principal;
        }

        @Override
        public Principal getSubjectDN() {
            return principal;
        }

        @Override
        public X500Principal getIssuerX500Principal() {
            return principal;
        }

        @Override
        public X500Principal getSubjectX500Principal() {
            return principal;
        }

        @Override
        public Date getNotBefore() {
            return new Date(0L);
        }

        @Override
        public Date getNotAfter() {
            return new Date(Long.MAX_VALUE);
        }

        @Override
        public byte[] getTBSCertificate() throws CertificateEncodingException {
            return new byte[0];
        }

        @Override
        public byte[] getSignature() {
            return new byte[0];
        }

        @Override
        public String getSigAlgName() {
            return "ML-DSA";
        }

        @Override
        public String getSigAlgOID() {
            return "1.2.3.4";
        }

        @Override
        public byte[] getSigAlgParams() {
            return new byte[0];
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return null;
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return null;
        }

        @Override
        public boolean[] getKeyUsage() {
            return null;
        }

        @Override
        public int getBasicConstraints() {
            return -1;
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            return new byte[0];
        }

        @Override
        public void verify(PublicKey key) {
        }

        @Override
        public void verify(PublicKey key, String sigProvider) {
        }

        @Override
        public String toString() {
            return "MinimalX509Certificate";
        }

        @Override
        public PublicKey getPublicKey() {
            return publicKey;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return Set.of();
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return Set.of();
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            return null;
        }

        @Override
        public List<String> getExtendedKeyUsage() {
            return null;
        }

        @Override
        public Collection<List<?>> getSubjectAlternativeNames() {
            return null;
        }

        @Override
        public Collection<List<?>> getIssuerAlternativeNames() {
            return null;
        }
    }
}
