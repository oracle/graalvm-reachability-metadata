/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_resource.connector_api;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.resource.spi.security.PasswordCredential;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PasswordCredentialTest {
    @Test
    void copiesTheSuppliedPasswordForSignOn() {
        char[] password = "secret".toCharArray();
        PasswordCredential credential = new PasswordCredential("service-user", password);
        Arrays.fill(password, '\0');

        assertEquals("service-user", credential.getUserName());
        assertArrayEquals("secret".toCharArray(), credential.getPassword());
        assertNotSame(password, credential.getPassword());
    }

    @Test
    void letsTheCallerEraseTheStoredPassword() {
        PasswordCredential credential = new PasswordCredential("service-user", "secret".toCharArray());

        Arrays.fill(credential.getPassword(), '\0');

        assertArrayEquals(new char[6], credential.getPassword());
    }

    @Test
    void matchesCredentialsByUsernameAndPasswordInASet() {
        PasswordCredential credential = new PasswordCredential("service-user", "secret".toCharArray());
        PasswordCredential equivalent = new PasswordCredential("service-user", "secret".toCharArray());
        Set<PasswordCredential> credentials = new HashSet<>();
        credentials.add(credential);

        assertEquals(credential, equivalent);
        assertEquals(credential.hashCode(), equivalent.hashCode());
        assertTrue(credentials.contains(equivalent));
        assertNotEquals(credential, new PasswordCredential("other-user", "secret".toCharArray()));
        assertNotEquals(credential, new PasswordCredential("service-user", "secrex".toCharArray()));
        assertNotEquals(credential, new PasswordCredential("service-user", "short".toCharArray()));
        assertNotEquals(credential, "service-user");
        assertNotEquals(null, credential);
    }
}
