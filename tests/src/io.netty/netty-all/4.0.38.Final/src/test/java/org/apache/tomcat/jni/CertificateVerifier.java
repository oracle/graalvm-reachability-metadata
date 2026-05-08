/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tomcat.jni;

public interface CertificateVerifier {
    int X509_V_OK = 0;
    int X509_V_ERR_UNSPECIFIED = 1;
    int X509_V_ERR_CERT_HAS_EXPIRED = 10;
    int X509_V_ERR_CERT_NOT_YET_VALID = 9;
    int X509_V_ERR_CERT_REVOKED = 23;
    int X509_V_ERR_DANE_NO_MATCH = 65;

    int verify(long ssl, byte[][] chain, String auth);
}
