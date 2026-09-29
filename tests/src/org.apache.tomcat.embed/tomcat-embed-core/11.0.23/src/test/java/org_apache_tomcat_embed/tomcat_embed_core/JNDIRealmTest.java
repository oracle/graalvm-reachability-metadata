/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.apache.catalina.realm.JNDIRealm;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JNDIRealmTest {

    @Test
    void createsConfiguredHostnameVerifier() {
        JNDIRealm realm = new JNDIRealm();
        realm.setHostnameVerifierClassName(AcceptAllHostnameVerifier.class.getName());

        HostnameVerifier verifier = realm.getHostnameVerifier();

        assertThat(verifier).isInstanceOf(AcceptAllHostnameVerifier.class);
        assertThat(verifier.verify("service.example", null)).isTrue();
        assertThat(realm.getHostnameVerifier()).isSameAs(verifier);
    }

    public static class AcceptAllHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return hostname.endsWith(".example");
        }
    }
}
