/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.security.SecureRandom;

import org.apache.catalina.util.StandardSessionIdGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SessionIdGeneratorBaseTest {
    @Test
    void generatesUniqueRoutedIdentifiersWithConfiguredRandom() {
        StandardSessionIdGenerator generator = new StandardSessionIdGenerator();
        generator.setSecureRandomClass(SecureRandom.class.getName());
        generator.setSessionIdLength(24);
        generator.setJvmRoute("worker");
        String first = generator.generateSessionId();
        String second = generator.generateSessionId();
        assertTrue(first.matches("[0-9A-F]{48}\\.worker"));
        assertTrue(second.matches("[0-9A-F]{48}\\.worker"));
        assertNotEquals(first, second);
    }
}
