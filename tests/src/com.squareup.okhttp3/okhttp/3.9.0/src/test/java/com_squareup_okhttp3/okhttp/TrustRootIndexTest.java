/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collections;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

public class TrustRootIndexTest {
    @Test
    void certificatePinningUsesAndroidStyleTrustManagerMethod()
            throws GeneralSecurityException, SSLPeerUnverifiedException {
        X509Certificate trustedCertificate = systemTrustManager().getAcceptedIssuers()[0];
        AndroidStyleTrustManager trustManager = new AndroidStyleTrustManager(trustedCertificate);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {trustManager}, null);
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        CertificatePinner certificatePinner = new CertificatePinner.Builder()
                .add("example.com", CertificatePinner.pin(trustedCertificate))
                .build();
        OkHttpClient client = new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustManager)
                .certificatePinner(certificatePinner)
                .build();

        client.certificatePinner()
                .check("example.com", Collections.singletonList(trustedCertificate));

        assertThat(client.sslSocketFactory()).isSameAs(sslSocketFactory);
        assertThat(trustManager.invocationCount).isEqualTo(1);
    }

    private static X509TrustManager systemTrustManager() throws GeneralSecurityException {
        TrustManagerFactory factory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        for (TrustManager trustManager : factory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        throw new IllegalStateException("No system X509 trust manager available");
    }

    public static final class AndroidStyleTrustManager implements X509TrustManager {
        private final X509Certificate trustedCertificate;
        private int invocationCount;

        AndroidStyleTrustManager(X509Certificate trustedCertificate) {
            this.trustedCertificate = trustedCertificate;
        }

        @SuppressWarnings("unused")
        private TrustAnchor findTrustAnchorByIssuerAndSignature(X509Certificate certificate) {
            invocationCount++;
            return certificate.equals(trustedCertificate)
                    ? new TrustAnchor(trustedCertificate, null)
                    : null;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[] {trustedCertificate};
        }
    }
}
