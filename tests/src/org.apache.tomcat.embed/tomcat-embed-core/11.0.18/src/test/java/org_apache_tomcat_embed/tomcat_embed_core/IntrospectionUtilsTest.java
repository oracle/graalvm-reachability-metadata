/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.ArrayList;
import java.util.List;

import org.apache.tomcat.util.IntrospectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionUtilsTest {

    @Test
    void invokesNamedMethodWithTypedArguments() throws Exception {
        List<String> values = new ArrayList<>();

        Object result = IntrospectionUtils.callMethodN(values, "add", new Object[] {"tomcat"},
                new Class<?>[] {Object.class});

        assertThat(result).isEqualTo(Boolean.TRUE);
        assertThat(values).containsExactly("tomcat");
    }
}
