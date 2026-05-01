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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class IvyAuthenticatorTest {
    private static final String JAVA_SPECIFICATION_VERSION = "java.specification.version";
    private static final String AUTHENTICATION_HOST = "ivy-authenticator.example.invalid";
    private static final char[] PASSWORD = "secret".toCharArray();

    private final Authenticator originalAuthenticator = Authenticator.getDefault();
    private final String originalJavaSpecificationVersion = System.getProperty(
            JAVA_SPECIFICATION_VERSION);

    @AfterEach
    void restoreGlobalState() {
        Authenticator.setDefault(originalAuthenticator);
        if (originalJavaSpecificationVersion == null) {
            System.clearProperty(JAVA_SPECIFICATION_VERSION);
        } else {
            System.setProperty(JAVA_SPECIFICATION_VERSION, originalJavaSpecificationVersion);
        }
    }

    @Test
    void installObtainsOriginalAuthenticatorThroughJavaNineAccessor() {
        System.setProperty(JAVA_SPECIFICATION_VERSION, "9");

        PasswordAuthentication authentication = installAndRequestAuthentication("modern-user");

        assertThat(authentication.getUserName()).isEqualTo("modern-user");
        assertThat(authentication.getPassword()).containsExactly(PASSWORD);
    }

    @Test
    void installObtainsOriginalAuthenticatorThroughLegacyFieldAccessor() {
        System.setProperty(JAVA_SPECIFICATION_VERSION, "1.8");

        PasswordAuthentication authentication = installAndRequestAuthentication("legacy-user");

        assertThat(authentication.getUserName()).isEqualTo("legacy-user");
        assertThat(authentication.getPassword()).containsExactly(PASSWORD);
    }

    private PasswordAuthentication installAndRequestAuthentication(String userName) {
        FixedAuthenticator delegate = new FixedAuthenticator(userName, PASSWORD);
        Authenticator.setDefault(delegate);

        IvyAuthenticator.install();

        assertThat(Authenticator.getDefault()).isNotSameAs(delegate);
        PasswordAuthentication authentication = Authenticator.requestPasswordAuthentication(
                AUTHENTICATION_HOST,
                null,
                80,
                "http",
                "Ivy test realm",
                "basic");
        assertThat(authentication).isNotNull();
        return authentication;
    }

    private static final class FixedAuthenticator extends Authenticator {
        private final String userName;
        private final char[] password;

        private FixedAuthenticator(String userName, char[] password) {
            this.userName = userName;
            this.password = password.clone();
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(userName, password.clone());
        }
    }
}
