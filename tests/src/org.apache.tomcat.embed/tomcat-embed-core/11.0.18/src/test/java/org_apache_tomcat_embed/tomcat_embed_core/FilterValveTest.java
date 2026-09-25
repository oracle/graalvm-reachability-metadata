/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.FilterValve;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class FilterValveTest {

    @Test
    void startsAndDestroysConfiguredFilter(@TempDir Path directory) throws Exception {
        RecordingFilter.INITIALIZED.set(false);
        RecordingFilter.DESTROYED.set(false);
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("base").toString());
        Context context = tomcat.addContext("/filter", directory.toString());
        FilterValve valve = new FilterValve();
        valve.setContainer(context);
        valve.setFilterClassName(RecordingFilter.class.getName());
        valve.addInitParam("mode", "recording");

        try {
            valve.start();
            assertThat(RecordingFilter.INITIALIZED).isTrue();
        } finally {
            valve.stop();
            valve.destroy();
            tomcat.destroy();
        }

        assertThat(RecordingFilter.DESTROYED).isTrue();
    }

    public static class RecordingFilter implements Filter {
        static final AtomicBoolean INITIALIZED = new AtomicBoolean();
        static final AtomicBoolean DESTROYED = new AtomicBoolean();

        @Override
        public void init(FilterConfig filterConfig) {
            INITIALIZED.set("recording".equals(filterConfig.getInitParameter("mode")));
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
            DESTROYED.set(true);
        }
    }
}
