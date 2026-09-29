/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.ServletRequestWrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletRequestWrapperTest {

    @Test
    void delegatesAttributesToWrappedCatalinaRequest() {
        Request request = new Request(new Connector(), new org.apache.coyote.Request());
        ServletRequestWrapper wrapper = new ServletRequestWrapper(request);

        wrapper.setAttribute("component", "tomcat");

        assertThat(wrapper.getAttribute("component")).isEqualTo("tomcat");
        assertThat(wrapper.getRequest()).isSameAs(request);
    }
}
