/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.Hashtable;

import jakarta.servlet.http.HttpUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class HttpUtilsTest {

    @Test
    void parsesUrlEncodedQueryParameters() {
        Hashtable<String, String[]> parameters = HttpUtils.parseQueryString("name=Apache+Tomcat&mode=embedded");

        assertThat(parameters.get("name")).containsExactly("Apache Tomcat");
        assertThat(parameters.get("mode")).containsExactly("embedded");
    }
}
