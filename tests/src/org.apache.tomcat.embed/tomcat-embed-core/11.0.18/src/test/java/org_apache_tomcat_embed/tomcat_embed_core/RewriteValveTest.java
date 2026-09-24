/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.valves.rewrite.InternalRewriteMap;
import org.apache.catalina.valves.rewrite.RewriteMap;
import org.apache.catalina.valves.rewrite.RewriteValve;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RewriteValveTest {

    @Test
    void createsNamedRewriteMapFromConfiguration() {
        String definition = "RewriteMap lower " + InternalRewriteMap.LowerCase.class.getName();

        Object[] parsed = (Object[]) RewriteValve.parse(definition);

        assertThat(parsed[0]).isEqualTo("lower");
        assertThat(((RewriteMap) parsed[1]).lookup("ToMcAt")).isEqualTo("tomcat");
    }
}
