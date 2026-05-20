/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_mail.jakarta_mail_api;

import jakarta.mail.Session;
import java.lang.reflect.Method;
import java.net.URL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SessionAnonymous7Test {
    private static final String TEST_CLASS_RESOURCE = "jakarta_mail/jakarta_mail_api/SessionAnonymous7Test.class";

    @Test
    void getSystemResourcesUsesSystemClassLoaderEnumeration() throws Exception {
        Method getSystemResources = Session.class.getDeclaredMethod("getSystemResources", String.class);
        getSystemResources.setAccessible(true);

        URL[] resources = (URL[]) getSystemResources.invoke(null, TEST_CLASS_RESOURCE);

        assertNotNull(resources);
        assertTrue(resources.length > 0);
    }
}
