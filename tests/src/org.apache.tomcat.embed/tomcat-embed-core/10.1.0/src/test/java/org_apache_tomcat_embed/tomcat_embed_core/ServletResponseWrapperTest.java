/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.ServletResponseWrapper;
import org.apache.catalina.connector.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletResponseWrapperTest {

    @Test
    void delegatesContentTypeToWrappedCatalinaResponse() {
        Response response = new Response(new org.apache.coyote.Response());
        ServletResponseWrapper wrapper = new ServletResponseWrapper(response);

        wrapper.setContentType("text/plain");

        assertThat(wrapper.getContentType()).isEqualTo("text/plain");
        assertThat(wrapper.getResponse()).isSameAs(response);
    }
}
