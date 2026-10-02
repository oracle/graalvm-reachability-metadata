/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package android.net.http;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.X509TrustManager;

public final class X509TrustManagerExtensions {
    private static String lastHostname;
    private final X509TrustManager trustManager;

    public X509TrustManagerExtensions(X509TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    public List<Certificate> checkServerTrusted(X509Certificate[] chain, String authType,
            String hostname) {
        lastHostname = hostname;
        return Arrays.asList(chain);
    }

    public X509TrustManager trustManager() {
        return trustManager;
    }

    public static String lastHostname() {
        return lastHostname;
    }
}
