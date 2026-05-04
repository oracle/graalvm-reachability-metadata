/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import org.apache.ivy.util.url.IvyAuthenticator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class IvyAuthenticatorTest {

    private static final Object AUTHENTICATOR_LOCK = new Object();

    private Authenticator originalAuthenticator;
    private String originalJavaSpecificationVersion;

    @BeforeEach
    void rememberGlobalState() {
        synchronized (AUTHENTICATOR_LOCK) {
            originalAuthenticator = Authenticator.getDefault();
            originalJavaSpecificationVersion = System.getProperty("java.specification.version");
        }
    }

    @AfterEach
    void restoreGlobalState() {
        synchronized (AUTHENTICATOR_LOCK) {
            Authenticator.setDefault(originalAuthenticator);
            if (originalJavaSpecificationVersion == null) {
                System.clearProperty("java.specification.version");
            } else {
                System.setProperty("java.specification.version", originalJavaSpecificationVersion);
            }
        }
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void installFindsExistingAuthenticatorThroughJava9DefaultAccessor() throws Exception {
        synchronized (AUTHENTICATOR_LOCK) {
            PasswordAuthentication expected = new PasswordAuthentication(
                    "default-user",
                    "default-pass".toCharArray());
            Authenticator.setDefault(new FixedAuthenticator(expected));
            System.setProperty(
                    "java.specification.version",
                    Integer.toString(Runtime.version().feature()));

            IvyAuthenticator.install();

            assertThat(Authenticator.getDefault()).isInstanceOf(IvyAuthenticator.class);
            assertThat(requestServerAuthentication()).isSameAs(expected);
        }
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void installFindsExistingAuthenticatorThroughLegacyPrivateField() throws Exception {
        synchronized (AUTHENTICATOR_LOCK) {
            PasswordAuthentication expected = new PasswordAuthentication(
                    "legacy-user",
                    "legacy-pass".toCharArray());
            Authenticator.setDefault(new FixedAuthenticator(expected));
            System.setProperty("java.specification.version", "1.8");

            IvyAuthenticator.install();

            assertThat(Authenticator.getDefault()).isInstanceOf(IvyAuthenticator.class);
            assertThat(requestServerAuthentication()).isSameAs(expected);
        }
    }

    private static PasswordAuthentication requestServerAuthentication() throws Exception {
        return Authenticator.requestPasswordAuthentication(
                "ivy-authenticator.test",
                InetAddress.getLoopbackAddress(),
                80,
                "http",
                "ivy-authenticator-realm",
                "basic",
                new URL("http://ivy-authenticator.test/repository"),
                Authenticator.RequestorType.SERVER);
    }

    private static final class FixedAuthenticator extends Authenticator {
        private final PasswordAuthentication authentication;

        private FixedAuthenticator(PasswordAuthentication authentication) {
            this.authentication = authentication;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return authentication;
        }
    }
}
