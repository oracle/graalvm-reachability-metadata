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
import java.io.InputStream;
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

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.io.InputContextImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InputContextImplTest {
    @Test
    void exposesRequestHeadersAndInputStream() throws IOException {
        SimpleHttpServletRequest request = new SimpleHttpServletRequest();
        request.setHeader(DavConstants.HEADER_CONTENT_LANGUAGE, "en-US");
        request.setHeader(DavConstants.HEADER_CONTENT_LENGTH, "11");
        request.setHeader(DavConstants.HEADER_CONTENT_TYPE, "text/plain");
        request.setHeader("X-Jackrabbit-Trace", "trace-1");
        byte[] payload = "hello world".getBytes("UTF-8");
        InputStream inputStream = new ByteArrayInputStream(payload);

        long before = System.currentTimeMillis();
        InputContextImpl context = new InputContextImpl(request, inputStream);
        long modificationTime = context.getModificationTime();
        long after = System.currentTimeMillis();

        assertThat(context.hasStream()).isTrue();
        assertThat(context.getInputStream().readAllBytes()).isEqualTo(payload);
        assertThat(modificationTime).isBetween(before, after);
        assertThat(context.getContentLanguage()).isEqualTo("en-US");
        assertThat(context.getContentLength()).isEqualTo(payload.length);
        assertThat(context.getContentType()).isEqualTo("text/plain");
        assertThat(context.getProperty("x-jackrabbit-trace")).isEqualTo("trace-1");
    }

    @Test
    void supportsRequestsWithoutBodyStream() {
        SimpleHttpServletRequest request = new SimpleHttpServletRequest();
        request.setHeader(DavConstants.HEADER_CONTENT_LENGTH, "0");

        InputContextImpl context = new InputContextImpl(request, null);

        assertThat(context.hasStream()).isFalse();
        assertThat(context.getInputStream()).isNull();
        assertThat(context.getContentLength()).isZero();
    }

    @Test
    void rejectsMissingServletRequest() {
        assertThatThrownBy(() -> new InputContextImpl(null, new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request must not be null");
    }

    private static final class SimpleHttpServletRequest implements HttpServletRequest {
        private final Map<String, String> headers = new LinkedHashMap<String, String>();
        private byte[] body = new byte[0];

        private void setHeader(String name, String value) {
            headers.put(name, value);
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
            return "PUT";
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
            return "";
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
            return "/content/example.txt";
        }

        @Override
        public StringBuffer getRequestURL() {
            return new java.lang.StringBuffer("http://localhost/content/example.txt");
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
            return getHeader(DavConstants.HEADER_CONTENT_TYPE);
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
            return "http";
        }

        @Override
        public String getServerName() {
            return "localhost";
        }

        @Override
        public int getServerPort() {
            return 80;
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
            return false;
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
            return "localhost";
        }

        @Override
        public String getLocalAddr() {
            return "127.0.0.1";
        }

        @Override
        public int getLocalPort() {
            return 80;
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
