/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.jupiter.api.Test;

public class FilterHolderTest {
    @Test
    public void initializeCreatesFilterInstanceFromHeldClass() throws Exception {
        FilterHolder holder = new FilterHolder(RecordingFilter.class);
        holder.setName("recording");
        holder.setInitParameter("mode", "test");
        holder.setServletHandler(new ServletHandler());
        holder.start();
        try {
            holder.initialize();

            Filter filter = holder.getFilter();
            assertThat(filter).isInstanceOf(RecordingFilter.class);
            RecordingFilter recordingFilter = (RecordingFilter) filter;
            assertThat(recordingFilter.getFilterName()).isEqualTo("recording");
            assertThat(recordingFilter.getMode()).isEqualTo("test");
            assertThat(recordingFilter.isInitialized()).isTrue();
        } finally {
            holder.stop();
        }
    }

    public static class RecordingFilter implements Filter {
        private boolean initialized;
        private String filterName;
        private String mode;

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            initialized = true;
            filterName = filterConfig.getFilterName();
            mode = filterConfig.getInitParameter("mode");
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
            initialized = false;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public String getFilterName() {
            return filterName;
        }

        public String getMode() {
            return mode;
        }
    }
}
