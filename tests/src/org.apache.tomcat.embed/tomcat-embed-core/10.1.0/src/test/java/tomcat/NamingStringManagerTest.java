/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.naming.StringManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NamingStringManagerTest {
    @Test
    void formatsNamingErrorWithItsResourceBundle() {
        StringManager messages = StringManager.getManager("org.apache.naming");
        assertTrue(messages.getString("namingContext.alreadyBound", "orders").contains("orders"));
    }
}
