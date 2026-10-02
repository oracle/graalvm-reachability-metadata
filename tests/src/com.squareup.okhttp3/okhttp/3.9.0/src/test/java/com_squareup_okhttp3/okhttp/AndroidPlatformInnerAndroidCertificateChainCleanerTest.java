/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import android.net.http.X509TrustManagerExtensions;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.X509TrustManager;
import okhttp3.internal.platform.Platform;
import okhttp3.internal.tls.CertificateChainCleaner;
import org.junit.jupiter.api.Test;

public class AndroidPlatformInnerAndroidCertificateChainCleanerTest {
    @Test
    void androidCertificateChainCleanerInvokesTrustManagerExtensions() throws Exception {
        CertificateChainCleaner cleaner = Platform.get()
                .buildCertificateChainCleaner(new EmptyTrustManager());

        List<Certificate> cleanedChain = cleaner.clean(Collections.emptyList(), "example.com");

        assertThat(cleanedChain).isEmpty();
        assertThat(X509TrustManagerExtensions.lastHostname()).isEqualTo("example.com");
    }

    private static final class EmptyTrustManager implements X509TrustManager {
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
