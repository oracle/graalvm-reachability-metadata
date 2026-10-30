/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_javamail_1_4_spec;

import java.util.concurrent.Callable;

import javax.mail.internet.InternetAddress;

import org.apache.geronimo.osgi.locator.ProviderLocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProviderLocatorTest {

    @Test
    void discoversAndInvokesServiceUsingTheRequestingClassLoader() throws Exception {
        Object service = ProviderLocator.getService(Callable.class.getName(), ProviderLocatorTest.class, null);

        assertThat(service).isInstanceOf(Callable.class);
        Object result = ((Callable<?>) service).call();
        assertThat(result).isInstanceOf(InternetAddress.class);
        InternetAddress mailbox = (InternetAddress) result;
        mailbox.validate();
        assertThat(mailbox.getAddress()).isEqualTo("reader@example.org");
        assertThat(mailbox.getPersonal()).isEqualTo("Report Reader");
        assertThat(mailbox.toUnicodeString()).isEqualTo("Report Reader <reader@example.org>");
    }

    /// An application service discovered from its classpath service declaration.
    public static final class MailboxProvider implements Callable<InternetAddress> {
        @Override
        public InternetAddress call() throws Exception {
            return new InternetAddress("reader@example.org", "Report Reader");
        }
    }
}
