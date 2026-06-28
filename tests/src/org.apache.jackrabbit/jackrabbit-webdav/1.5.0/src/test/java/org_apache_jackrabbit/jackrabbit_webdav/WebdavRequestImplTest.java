/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.jackrabbit.webdav.AbstractLocatorFactory;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.WebdavRequestImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WebdavRequestImplTest {
    @Test
    void resolvesRequestAndDestinationLocatorsFromServletRequest() throws DavException {
        SimpleHttpServletRequest servletRequest = new SimpleHttpServletRequest();
        servletRequest.setScheme("https");
        servletRequest.setContextPath("/repo");
        servletRequest.setRequestUri("/repo/default/documents/report.txt");
        servletRequest.setHeader("Host", "dav.example.test:8443");
        servletRequest.setHeader(
                DavConstants.HEADER_DESTINATION,
                "https://dav.example.test:8443/repo/default/documents/copy.txt");

        WebdavRequestImpl request = new WebdavRequestImpl(servletRequest, new PathPreservingLocatorFactory("/repo"));

        DavResourceLocator requestLocator = request.getRequestLocator();
        DavResourceLocator destinationLocator = request.getDestinationLocator();
        DavResourceLocator hrefLocator = request.getHrefLocator("/repo/default/documents/from-href.txt");

        assertThat(requestLocator.getPrefix()).isEqualTo("https://dav.example.test:8443/repo");
        assertThat(requestLocator.getWorkspacePath()).isEqualTo("/default");
        assertThat(requestLocator.getResourcePath()).isEqualTo("/default/documents/report.txt");
        assertThat(requestLocator.getRepositoryPath()).isEqualTo("/documents/report.txt");
        assertThat(requestLocator.getHref(false))
                .isEqualTo("https://dav.example.test:8443/repo/default/documents/report.txt");

        assertThat(destinationLocator.getResourcePath()).isEqualTo("/default/documents/copy.txt");
        assertThat(destinationLocator.getRepositoryPath()).isEqualTo("/documents/copy.txt");
        assertThat(hrefLocator.getResourcePath()).isEqualTo("/default/documents/from-href.txt");
    }

    private static final class PathPreservingLocatorFactory extends AbstractLocatorFactory {
        private PathPreservingLocatorFactory(String pathPrefix) {
            super(pathPrefix);
        }

        @Override
        protected String getRepositoryPath(String resourcePath, String workspacePath) {
            if (resourcePath == null || workspacePath == null) {
                return resourcePath;
            }
            if (resourcePath.equals(workspacePath)) {
                return "/";
            }
            return resourcePath.substring(workspacePath.length());
        }

        @Override
        protected String getResourcePath(String repositoryPath, String workspacePath) {
            if (repositoryPath == null || workspacePath == null) {
                return repositoryPath;
            }
            if ("/".equals(repositoryPath)) {
                return workspacePath;
            }
            return workspacePath + repositoryPath;
        }
    }

    private static final class SimpleHttpServletRequest implements HttpServletRequest {
        private final Map<String, String> headers = new LinkedHashMap<String, String>();
        private String scheme = "http";
        private String contextPath = "";
        private String requestUri = "/";
        private byte[] body = new byte[0];

        private void setHeader(String name, String value) {
            headers.put(name, value);
        }

        private void setScheme(String scheme) {
            this.scheme = scheme;
        }

        private void setContextPath(String contextPath) {
            this.contextPath = contextPath;
        }

        private void setRequestUri(String requestUri) {
            this.requestUri = requestUri;
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
        public long getDateHeader(String name) {
            return -1L;
        }

        @Override
        public String getHeader(String name) {
            if (headers.containsKey(name)) {
                return headers.get(name);
            }
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        @Override
        public Enumeration getHeaders(String name) {
            String header = getHeader(name);
            if (header == null) {
                return Collections.enumeration(Collections.emptyList());
            }
            return Collections.enumeration(Collections.singletonList(header));
        }

        @Override
        public Enumeration getHeaderNames() {
            return Collections.enumeration(headers.keySet());
        }

        @Override
        public int getIntHeader(String name) {
            String header = getHeader(name);
            return header == null ? -1 : Integer.parseInt(header);
        }

        @Override
        public String getMethod() {
            return "GET";
        }

        @Override
        public String getPathInfo() {
            return null;
        }

        @Override
        public String getPathTranslated() {
            return null;
        }

        @Override
        public String getContextPath() {
            return contextPath;
        }

        @Override
        public String getQueryString() {
            return null;
        }

        @Override
        public String getRemoteUser() {
            return null;
        }

        @Override
        public boolean isUserInRole(String role) {
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
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            return null;
        }

        @Override
        public String getServletPath() {
            return "";
        }

        @Override
        public HttpSession getSession(boolean create) {
            return null;
        }

        @Override
        public HttpSession getSession() {
            return null;
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
        public boolean isRequestedSessionIdFromUrl() {
            return false;
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public Enumeration getAttributeNames() {
            return Collections.enumeration(Collections.emptyList());
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
            if (!"UTF-8".equalsIgnoreCase(env)) {
                throw new UnsupportedEncodingException(env);
            }
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ByteArrayServletInputStream(body);
        }

        @Override
        public String getParameter(String name) {
            return null;
        }

        @Override
        public Enumeration getParameterNames() {
            return Collections.enumeration(Collections.emptyList());
        }

        @Override
        public String[] getParameterValues(String name) {
            return null;
        }

        @Override
        public Map getParameterMap() {
            return Collections.emptyMap();
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public String getScheme() {
            return scheme;
        }

        @Override
        public String getServerName() {
            return "dav.example.test";
        }

        @Override
        public int getServerPort() {
            return 8443;
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body)));
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
        public void setAttribute(String name, Object o) {
        }

        @Override
        public void removeAttribute(String name) {
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

        @Override
        public Enumeration getLocales() {
            return Collections.enumeration(Collections.singletonList(Locale.ROOT));
        }

        @Override
        public boolean isSecure() {
            return "https".equalsIgnoreCase(scheme);
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            return null;
        }

        @Override
        public String getRealPath(String path) {
            return path;
        }

        @Override
        public int getRemotePort() {
            return 49152;
        }

        @Override
        public String getLocalName() {
            return "dav.example.test";
        }

        @Override
        public String getLocalAddr() {
            return "127.0.0.1";
        }

        @Override
        public int getLocalPort() {
            return 8443;
        }
    }

    private static final class ByteArrayServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream inputStream;

        private ByteArrayServletInputStream(byte[] body) {
            inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }
    }
}
