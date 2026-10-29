/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tools.ant.util.regexp.RegexpMatcher;
import org.apache.tools.ant.util.regexp.RegexpMatcherFactory;
import org.junit.jupiter.api.Test;

public class RegexpMatcherFactoryTest {
    @Test
    void discoversMatcherAndExtractsGroups() {
        RegexpMatcher matcher = new RegexpMatcherFactory().newRegexpMatcher();
        matcher.setPattern("^item-([0-9]+)$");
        assertTrue(matcher.matches("item-42"));
        assertEquals("42", matcher.getGroups("item-42").get(1));
        assertFalse(matcher.matches("other"));
    }
}
