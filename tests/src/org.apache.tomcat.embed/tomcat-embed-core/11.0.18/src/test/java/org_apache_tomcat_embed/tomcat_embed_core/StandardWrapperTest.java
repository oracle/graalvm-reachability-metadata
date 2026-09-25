/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.core.StandardWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardWrapperTest {

    @Test
    void reportsMethodsImplementedByConfiguredServlet() throws Exception {
        StandardWrapper wrapper = new StandardWrapper();
        wrapper.setName("methods");
        wrapper.setServlet(new PostServlet());

        assertThat(wrapper.getServletMethods()).contains("POST", "OPTIONS", "TRACE");
    }

    public static class PostServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }
}
