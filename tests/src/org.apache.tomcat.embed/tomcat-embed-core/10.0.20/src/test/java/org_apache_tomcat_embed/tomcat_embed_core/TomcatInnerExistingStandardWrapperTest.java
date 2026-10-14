/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.SingleThreadModel;
import jakarta.servlet.http.HttpServlet;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardWrapper;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class TomcatInnerExistingStandardWrapperTest {

    @Test
    void createsASeparateInitializedServletForSingleThreadModel() throws Exception {
        CountingServlet.reset();
        CountingServlet suppliedServlet = new CountingServlet();
        StandardContext context = new StandardContext();
        context.setName("existing-servlet-context");
        StandardWrapper wrapper = (StandardWrapper) Tomcat.addServlet(context, "counting", suppliedServlet);

        CountingServlet loadedServlet = (CountingServlet) wrapper.loadServlet();

        assertThat(loadedServlet).isNotSameAs(suppliedServlet);
        assertThat(CountingServlet.instances()).isEqualTo(2);
        assertThat(CountingServlet.initializations()).isEqualTo(1);
    }

    public static final class CountingServlet extends HttpServlet implements SingleThreadModel {

        private static final AtomicInteger INSTANCES = new AtomicInteger();
        private static final AtomicInteger INITIALIZATIONS = new AtomicInteger();

        public CountingServlet() {
            INSTANCES.incrementAndGet();
        }

        @Override
        public void init(ServletConfig config) throws ServletException {
            INITIALIZATIONS.incrementAndGet();
        }

        private static void reset() {
            INSTANCES.set(0);
            INITIALIZATIONS.set(0);
        }

        private static int instances() {
            return INSTANCES.get();
        }

        private static int initializations() {
            return INITIALIZATIONS.get();
        }
    }
}
