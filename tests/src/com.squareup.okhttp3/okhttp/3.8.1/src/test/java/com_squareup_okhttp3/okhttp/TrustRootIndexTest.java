/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import okhttp3.internal.tls.TrustRootIndex;
import org.junit.jupiter.api.Test;

public class TrustRootIndexTest {
    @Test
    void androidStyleTrustManagerMethodIsDiscoveredAndInvoked() {
        AndroidStyleTrustManager trustManager = new AndroidStyleTrustManager();
        TrustRootIndex trustRootIndex = TrustRootIndex.get(trustManager);

        assertThat(trustRootIndex.findByIssuerAndSignature(null)).isNull();
        assertThat(trustManager.invocationCount).isEqualTo(1);
    }

    public static final class AndroidStyleTrustManager implements X509TrustManager {
        private int invocationCount;

        @SuppressWarnings("unused")
        private TrustAnchor findTrustAnchorByIssuerAndSignature(X509Certificate certificate) {
            invocationCount++;
            return null;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
