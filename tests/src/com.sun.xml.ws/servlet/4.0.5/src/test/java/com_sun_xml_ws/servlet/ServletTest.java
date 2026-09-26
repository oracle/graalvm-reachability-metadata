/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.namespace.QName;

import com.sun.xml.ws.api.server.BoundEndpoint;
import com.sun.xml.ws.developer.servlet.HttpSessionScopeFeature;
import com.sun.xml.ws.transport.http.servlet.ServletAdapterList;
import com.sun.xml.ws.transport.http.servlet.ServletModule;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ReadListener;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import org.junit.jupiter.api.Test;

public class ServletTest {
    @Test
    void computesServletModuleAddressFromForwardingHeaders() {
        TestServletModule module = new TestServletModule();
        MemoryHttpServletRequest request = new MemoryHttpServletRequest("/application", "/application/services/echo");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "services.example.test");

        String address = module.getContextPath(request);

        assertThat(address).isEqualTo("https://services.example.test/application");
        assertThat(module.getContextPath()).isEqualTo("http://localhost:8080/application");
        assertThat(module.getBoundEndpoints()).isEmpty();
    }

    @Test
    void computesServletModuleAddressFromRequestWhenNotForwarded() {
        TestServletModule module = new TestServletModule();
        MemoryHttpServletRequest request = new MemoryHttpServletRequest("/application", "/application/services/echo");

        String address = module.getContextPath(request);

        assertThat(address).isEqualTo("http://localhost:8080/application");
    }

    @Test
    void resolvesNoPortAddressWhenServletAdapterListIsEmpty() {
        ServletAdapterList adapters = new ServletAdapterList(null);

        String address = adapters.createPortAddressResolver("https://services.example.test", ServletTest.class)
                .getAddressFor(new QName("urn:servlet-test", "EchoService"), "EchoPort");

        assertThat(adapters).isEmpty();
        assertThat(address).isNull();
    }

    @Test
    void exposesHttpSessionScopeFeatureContract() {
        HttpSessionScopeFeature feature = new HttpSessionScopeFeature();

        assertThat(feature.isEnabled()).isTrue();
        assertThat(feature.getID()).isEqualTo(HttpSessionScopeFeature.ID);
        assertThat(feature.getID()).isEqualTo("http://jax-ws.dev.java.net/features/servlet/httpSessionScope");
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Not used by this servlet transport test");
    }

    private static final class TestServletModule extends ServletModule {
        @Override
        public List<BoundEndpoint> getBoundEndpoints() {
            return Collections.emptyList();
        }

        @Override
        public String getContextPath() {
            return "http://localhost:8080/application";
        }
    }

    private static final class MemoryServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0]);

        @Override
        public int read() {
            return inputStream.read();
        }

        @Override
        public boolean isFinished() {
            return true;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
            throw unsupported();
        }
    }

    private static final class MemoryHttpServletRequest implements HttpServletRequest {
        private final String contextPath;
        private final String requestUri;
        private final Map<String, Object> attributes = new HashMap<>();
        private final Map<String, String> headers = new LinkedHashMap<>();

        private MemoryHttpServletRequest(final String contextPath, final String requestUri) {
            this.contextPath = contextPath;
            this.requestUri = requestUri;
        }

        private void addHeader(final String name, final String value) {
            headers.put(name, value);
        }

        @Override
        public String getMethod() {
            return "GET";
        }

        @Override
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            return new StringBuffer("http://localhost:8080").append(requestUri);
        }

        @Override
        public String getContextPath() {
            return contextPath;
        }

        @Override
        public String getServletPath() {
            return requestUri.substring(contextPath.length());
        }

        @Override
        public String getPathInfo() {
            return getServletPath();
        }

        @Override
        public String getPathTranslated() {
            return null;
        }

        @Override
        public String getQueryString() {
            return null;
        }

        @Override
        public String getHeader(final String name) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getKey().equalsIgnoreCase(name)) {
                    return header.getValue();
                }
            }
            return null;
        }

        @Override
        public Enumeration<String> getHeaders(final String name) {
            String value = getHeader(name);
            return value == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(value));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(headers.keySet());
        }

        @Override
        public long getDateHeader(final String name) {
            return -1;
        }

        @Override
        public int getIntHeader(final String name) {
            String value = getHeader(name);
            return value == null ? -1 : Integer.parseInt(value);
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public int getContentLength() {
            return 0;
        }

        @Override
        public long getContentLengthLong() {
            return 0;
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public void setCharacterEncoding(final String env) {
        }

        @Override
        public ServletInputStream getInputStream() {
            return new MemoryServletInputStream();
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(new byte[0]), StandardCharsets.UTF_8));
        }

        @Override
        public Object getAttribute(final String name) {
            return attributes.get(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        @Override
        public void setAttribute(final String name, final Object value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttribute(final String name) {
            attributes.remove(name);
        }

        @Override
        public String getParameter(final String name) {
            return null;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public String[] getParameterValues(final String name) {
            return null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.emptyMap();
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public String getScheme() {
            return "http";
        }

        @Override
        public String getServerName() {
            return "localhost";
        }

        @Override
        public int getServerPort() {
            return 8080;
        }

        @Override
        public String getRemoteAddr() {
            return "127.0.0.1";
        }

        @Override
        public String getRemoteHost() {
            return "localhost";
        }

        @Override
        public Locale getLocale() {
            return Locale.ENGLISH;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return Collections.enumeration(List.of(Locale.ENGLISH));
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(final String path) {
            return null;
        }

        @Override
        public int getRemotePort() {
            return 0;
        }

        @Override
        public String getLocalName() {
            return "localhost";
        }

        @Override
        public String getLocalAddr() {
            return "127.0.0.1";
        }

        @Override
        public int getLocalPort() {
            return 8080;
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public AsyncContext startAsync() {
            throw unsupported();
        }

        @Override
        public AsyncContext startAsync(final ServletRequest servletRequest, final ServletResponse servletResponse) {
            throw unsupported();
        }

        @Override
        public boolean isAsyncStarted() {
            return false;
        }

        @Override
        public boolean isAsyncSupported() {
            return false;
        }

        @Override
        public AsyncContext getAsyncContext() {
            return null;
        }

        @Override
        public DispatcherType getDispatcherType() {
            return DispatcherType.REQUEST;
        }

        @Override
        public String getRequestId() {
            return "request-1";
        }

        @Override
        public String getProtocolRequestId() {
            return "request-1";
        }

        @Override
        public ServletConnection getServletConnection() {
            return null;
        }

        @Override
        public String getAuthType() {
            return null;
        }

        @Override
        public Cookie[] getCookies() {
            return new Cookie[0];
        }

        @Override
        public String getRemoteUser() {
            return null;
        }

        @Override
        public boolean isUserInRole(final String role) {
            return false;
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public String getRequestedSessionId() {
            return null;
        }

        @Override
        public HttpSession getSession(final boolean create) {
            return null;
        }

        @Override
        public HttpSession getSession() {
            return null;
        }

        @Override
        public String changeSessionId() {
            throw unsupported();
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL() {
            return false;
        }

        @Override
        public boolean authenticate(final HttpServletResponse response) {
            throw unsupported();
        }

        @Override
        public void login(final String username, final String password) {
            throw unsupported();
        }

        @Override
        public void logout() {
            throw unsupported();
        }

        @Override
        public Collection<Part> getParts() {
            return Collections.emptyList();
        }

        @Override
        public Part getPart(final String name) {
            return null;
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(final Class<T> handlerClass) {
            throw unsupported();
        }
    }
}
