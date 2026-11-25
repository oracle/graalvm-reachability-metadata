/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Locale;

import org.apache.catalina.valves.rewrite.RewriteMap;
import org.apache.catalina.valves.rewrite.RewriteValve;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RewriteValveTest {
    @Test
    void constructsConfiguredRewriteMapAndPassesItsParameters() {
        Object[] parsed = (Object[]) RewriteValve.parse("RewriteMap catalog " + CatalogMap.class.getName() + " item-");
        assertThat(parsed[0]).isEqualTo("catalog");
        RewriteMap map = (RewriteMap) parsed[1];
        assertThat(map.lookup("BOOK")).isEqualTo("item-book");
    }

    public static class CatalogMap implements RewriteMap {
        private String prefix;

        @Override
        public String setParameters(String parameters) {
            prefix = parameters;
            return null;
        }

        @Override
        public String lookup(String key) {
            return prefix + key.toLowerCase(Locale.ROOT);
        }
    }
}
