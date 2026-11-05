/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.io.BufferedReader;
import java.io.StringWriter;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

final class WebRequest implements HttpServletRequest {
    final Map<String, Object> attributes = new HashMap<>();
    final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }
    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }
    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }
    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }
    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }
    @Override
    public Enumeration<String> getHeaders(String name) {
        String value = headers.get(name);
        return Collections.enumeration(value == null ? List.of() : List.of(value));
    }
    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }
    @Override
    public String getMethod() {
        return "GET";
    }
    @Override
    public String getRequestURI() {
        return "/orders";
    }
    @Override
    public String getQueryString() {
        return "view=summary";
    }
    @Override
    public StringBuffer getRequestURL() {
        StringWriter url = new StringWriter();
        url.write("http://localhost/orders");
        return url.getBuffer();
    }
    @Override
    public String getAuthType() {
        throw new UnsupportedOperationException();
    }
    @Override
    public Cookie[] getCookies() {
        throw new UnsupportedOperationException();
    }
    @Override
    public long getDateHeader(String name) {
        throw new UnsupportedOperationException();
    }
    @Override
    public int getIntHeader(String name) {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getPathInfo() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getPathTranslated() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getContextPath() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getRemoteUser() {
        throw new UnsupportedOperationException();
    }
    @Override
    public boolean isUserInRole(String role) {
        throw new UnsupportedOperationException();
    }
    @Override
    public Principal getUserPrincipal() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getRequestedSessionId() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getServletPath() {
        throw new UnsupportedOperationException();
    }
    @Override
    public HttpSession getSession(boolean create) {
        throw new UnsupportedOperationException();
    }
    @Override
    public HttpSession getSession() {
        throw new UnsupportedOperationException();
    }
    @Override
    public boolean isRequestedSessionIdValid() {
        throw new UnsupportedOperationException();
    }
    @Override
    public boolean isRequestedSessionIdFromCookie() {
        throw new UnsupportedOperationException();
    }
    @Override
    public boolean isRequestedSessionIdFromURL() {
        throw new UnsupportedOperationException();
    }
    @Override
    public boolean isRequestedSessionIdFromUrl() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getCharacterEncoding() {
        throw new UnsupportedOperationException();
    }
    @Override
    public void setCharacterEncoding(String encoding) {
        throw new UnsupportedOperationException();
    }
    @Override
    public int getContentLength() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getContentType() {
        throw new UnsupportedOperationException();
    }
    @Override
    public ServletInputStream getInputStream() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getParameter(String name) {
        throw new UnsupportedOperationException();
    }
    @Override
    public Enumeration<String> getParameterNames() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String[] getParameterValues(String name) {
        throw new UnsupportedOperationException();
    }
    @Override
    public Map<String, String[]> getParameterMap() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getProtocol() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getScheme() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getServerName() {
        throw new UnsupportedOperationException();
    }
    @Override
    public int getServerPort() {
        throw new UnsupportedOperationException();
    }
    @Override
    public BufferedReader getReader() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getRemoteAddr() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getRemoteHost() {
        throw new UnsupportedOperationException();
    }
    @Override
    public Locale getLocale() {
        throw new UnsupportedOperationException();
    }
    @Override
    public Enumeration<Locale> getLocales() {
        throw new UnsupportedOperationException();
    }
    @Override
    public boolean isSecure() {
        throw new UnsupportedOperationException();
    }
    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getRealPath(String path) {
        throw new UnsupportedOperationException();
    }
    @Override
    public int getRemotePort() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getLocalName() {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getLocalAddr() {
        throw new UnsupportedOperationException();
    }
    @Override
    public int getLocalPort() {
        throw new UnsupportedOperationException();
    }
}
