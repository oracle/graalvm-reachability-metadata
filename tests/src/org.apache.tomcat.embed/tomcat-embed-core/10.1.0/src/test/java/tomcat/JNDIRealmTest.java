/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.apache.catalina.realm.JNDIRealm;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JNDIRealmTest {
    @Test
    void constructsAndCachesConfiguredHostnameVerifier() {
        JNDIRealm realm = new JNDIRealm();
        realm.setHostnameVerifierClassName(ServiceHostnameVerifier.class.getName());
        HostnameVerifier verifier = realm.getHostnameVerifier();
        assertThat(verifier.verify("directory.example.test", null)).isTrue();
        assertThat(verifier.verify("untrusted.example.test", null)).isFalse();
        assertThat(realm.getHostnameVerifier()).isSameAs(verifier);
    }

    public static class ServiceHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return "directory.example.test".equals(hostname);
        }
    }
}
