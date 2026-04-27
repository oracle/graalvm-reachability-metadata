/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

import org.apache.ivy.util.url.IvyAuthenticator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock("java.net.Authenticator")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class IvyAuthenticatorTest {
    @Test
    public void installsWithModernAndLegacyAuthenticatorDiscovery() {
        String previousJavaSpecificationVersion = System.getProperty("java.specification.version");
        Authenticator previousAuthenticator = Authenticator.getDefault();
        try {
            Authenticator legacyOriginal = new FixedAuthenticator("legacy-user");
            installWithJavaSpecificationVersion("1.8", legacyOriginal);

            PasswordAuthentication legacyAuthentication =
                    Authenticator.requestPasswordAuthentication(
                            "repository.example", null, 443, "https", "integration-realm", "basic");
            assertThat(legacyAuthentication).isNotNull();
            assertThat(legacyAuthentication.getUserName()).isEqualTo("legacy-user");

            Authenticator modernOriginal = new FixedAuthenticator("modern-user");
            installWithJavaSpecificationVersion("9", modernOriginal);

            PasswordAuthentication modernAuthentication =
                    Authenticator.requestPasswordAuthentication(
                            "repository.example", null, 443, "https", "integration-realm", "basic");
            assertThat(modernAuthentication).isNotNull();
            assertThat(modernAuthentication.getUserName()).isEqualTo("modern-user");
        } finally {
            Authenticator.setDefault(previousAuthenticator);
            restoreJavaSpecificationVersion(previousJavaSpecificationVersion);
        }
    }

    private static void installWithJavaSpecificationVersion(
            String javaSpecificationVersion, Authenticator originalAuthenticator) {
        Authenticator.setDefault(originalAuthenticator);
        System.setProperty("java.specification.version", javaSpecificationVersion);

        IvyAuthenticator.install();

        assertThat(Authenticator.getDefault())
                .isInstanceOf(IvyAuthenticator.class)
                .isNotSameAs(originalAuthenticator);
    }

    private static void restoreJavaSpecificationVersion(String javaSpecificationVersion) {
        if (javaSpecificationVersion == null) {
            System.clearProperty("java.specification.version");
        } else {
            System.setProperty("java.specification.version", javaSpecificationVersion);
        }
    }

    private static final class FixedAuthenticator extends Authenticator {
        private final String userName;

        private FixedAuthenticator(String userName) {
            this.userName = userName;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(userName, new char[] {'s', 'e', 'c', 'r', 'e', 't'});
        }
    }
}
