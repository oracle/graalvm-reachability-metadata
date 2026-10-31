/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.catalina.valves.rewrite.RewriteMap;
import org.apache.catalina.valves.rewrite.RewriteValve;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RewriteValveTest {
    @Test
    void loadsAndConfiguresApplicationRewriteMap() {
        Object[] definition = (Object[]) RewriteValve.parse(
                "RewriteMap route " + PrefixMap.class.getName() + " /service/");
        assertEquals("route", definition[0]);
        RewriteMap map = (RewriteMap) definition[1];
        assertEquals("/service/orders", map.lookup("orders"));
    }

    public static class PrefixMap implements RewriteMap {
        private String prefix;

        @Override
        public String setParameters(String parameters) {
            prefix = parameters;
            return null;
        }

        @Override
        public String lookup(String key) {
            return prefix + key;
        }
    }
}
