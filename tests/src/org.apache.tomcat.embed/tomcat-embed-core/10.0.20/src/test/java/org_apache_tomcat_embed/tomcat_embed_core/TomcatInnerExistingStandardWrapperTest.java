/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.SingleThreadModel;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.Tomcat.ExistingStandardWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TomcatInnerExistingStandardWrapperTest {

    @Test
    void createsASeparateInstanceForSingleThreadServlets() throws Exception {
        Context context = new Tomcat().addContext("", ".");
        Wrapper wrapper = Tomcat.addServlet(context, "single-thread", new SingleThreadServlet());

        Servlet loaded = ((ExistingStandardWrapper) wrapper).loadServlet();

        assertThat(loaded).isInstanceOf(SingleThreadServlet.class);
        assertThat(loaded).isNotSameAs(wrapper.getServlet());
    }

    public static class SingleThreadServlet implements Servlet, SingleThreadModel {

        @Override
        public void init(ServletConfig config) throws ServletException {
        }

        @Override
        public ServletConfig getServletConfig() {
            return null;
        }

        @Override
        public void service(ServletRequest request, ServletResponse response) {
        }

        @Override
        public String getServletInfo() {
            return "single-thread";
        }

        @Override
        public void destroy() {
        }
    }
}
