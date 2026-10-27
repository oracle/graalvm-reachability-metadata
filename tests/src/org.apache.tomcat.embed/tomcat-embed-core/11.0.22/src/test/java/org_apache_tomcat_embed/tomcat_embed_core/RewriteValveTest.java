/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.valves.rewrite.RewriteMap;
import org.apache.catalina.valves.rewrite.RewriteValve;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RewriteValveTest {

    @Test
    void configuresCustomRewriteMapByClassName() throws Exception {
        RewriteValve valve = new RewriteValve();
        StandardContext context = new StandardContext();
        context.setName("rewrite-test");
        valve.setContainer(context);
        String configuration = "RewriteMap custom " + PrefixRewriteMap.class.getName() + " prefix-";

        valve.setConfiguration(configuration);
        Object[] parsed = (Object[]) RewriteValve.parse(configuration);
        RewriteMap map = (RewriteMap) parsed[1];

        assertThat(map.lookup("value")).isEqualTo("prefix-value");
        assertThat(valve.getConfiguration()).contains("RewriteMap custom", PrefixRewriteMap.class.getName());
    }

    public static final class PrefixRewriteMap implements RewriteMap {

        private String prefix = "";

        public PrefixRewriteMap() {
        }

        @Override
        public String setParameters(String params) {
            prefix = params;
            return null;
        }

        @Override
        public String lookup(String key) {
            return prefix + key;
        }
    }
}
