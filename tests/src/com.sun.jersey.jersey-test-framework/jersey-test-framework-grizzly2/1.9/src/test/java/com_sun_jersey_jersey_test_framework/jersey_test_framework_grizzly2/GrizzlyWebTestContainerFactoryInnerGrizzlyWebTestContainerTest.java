/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey_jersey_test_framework.jersey_test_framework_grizzly2;

import com.sun.jersey.test.framework.WebAppDescriptor;
import com.sun.jersey.test.framework.spi.container.TestContainer;
import com.sun.jersey.test.framework.spi.container.grizzly2.web.GrizzlyWebTestContainerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GrizzlyWebTestContainerFactoryInnerGrizzlyWebTestContainerTest {
    @Test
    @Timeout(120)
    void servesRequestsThroughConfiguredServletAndFilter() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        URI baseUri = URI.create("http://127.0.0.1:" + port + "/");
        WebAppDescriptor descriptor = new WebAppDescriptor.Builder()
                .servletClass(GreetingServlet.class)
                .initParam("greeting", "Hello")
                .addFilter(GreetingFilter.class, "greeting-filter", Map.of("suffix", "from filter"))
                .build();
        TestContainer container = new GrizzlyWebTestContainerFactory().create(baseUri, descriptor);
        try {
            container.start();
            assertEquals(baseUri, container.getBaseUri());
            HttpURLConnection connection = (HttpURLConnection) baseUri.resolve("?name=Ada").toURL().openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            try {
                assertEquals(200, connection.getResponseCode());
                try (InputStream response = connection.getInputStream()) {
                    assertEquals("Hello Ada from filter", new String(response.readAllBytes(), StandardCharsets.UTF_8));
                }
            } finally {
                connection.disconnect();
            }
        } finally {
            container.stop();
        }
    }

    public static class GreetingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(getInitParameter("greeting") + " " + request.getParameter("name")
                    + " " + request.getAttribute("suffix"));
        }
    }

    public static class GreetingFilter implements Filter {
        private String suffix;

        @Override
        public void init(FilterConfig config) {
            suffix = config.getInitParameter("suffix");
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            request.setAttribute("suffix", suffix);
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
            suffix = null;
        }
    }
}
