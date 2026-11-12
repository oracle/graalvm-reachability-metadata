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

import static org.assertj.core.api.Assertions.assertThat;

public class SessionIdGeneratorBaseTest {
    @Test
    void constructsConfiguredRandomSourceForSessionIdentifiers() {
        StandardSessionIdGenerator generator = new StandardSessionIdGenerator();
        generator.setSecureRandomClass(SecureRandom.class.getName());
        generator.setSessionIdLength(24);
        String first = generator.generateSessionId("worker");
        assertThat(first).matches("[0-9A-F]{48}\\.worker");
        assertThat(generator.generateSessionId("worker")).isNotEqualTo(first);
    }
}
