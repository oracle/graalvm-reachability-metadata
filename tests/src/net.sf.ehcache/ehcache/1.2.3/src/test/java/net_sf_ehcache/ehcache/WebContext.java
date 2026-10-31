/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;

final class WebContext implements ServletContext, FilterConfig {
    final Map<String, String> parameters = new HashMap<>();
    private final Map<String, Object> attributes = new HashMap<>();

    @Override
    public String getFilterName() { return "page-filter"; }
    @Override
    public ServletContext getServletContext() { return this; }
    @Override
    public String getInitParameter(String name) { return parameters.get(name); }
    @Override
    public Enumeration<String> getInitParameterNames() { return Collections.enumeration(parameters.keySet()); }
    @Override
    public Object getAttribute(String name) { return attributes.get(name); }
    @Override
    public Enumeration<String> getAttributeNames() { return Collections.enumeration(attributes.keySet()); }
    @Override
    public void setAttribute(String name, Object value) { attributes.put(name, value); }
    @Override
    public void removeAttribute(String name) { attributes.remove(name); }
    @Override
    public String getContextPath() { return ""; }
    @Override
    public ServletContext getContext(String path) { throw new UnsupportedOperationException(); }
    @Override
    public int getMajorVersion() { throw new UnsupportedOperationException(); }
    @Override
    public int getMinorVersion() { throw new UnsupportedOperationException(); }
    @Override
    public String getMimeType(String file) { throw new UnsupportedOperationException(); }
    @Override
    public Set<String> getResourcePaths(String path) { throw new UnsupportedOperationException(); }
    @Override
    public URL getResource(String path) { throw new UnsupportedOperationException(); }
    @Override
    public InputStream getResourceAsStream(String path) { throw new UnsupportedOperationException(); }
    @Override
    public RequestDispatcher getRequestDispatcher(String path) { throw new UnsupportedOperationException(); }
    @Override
    public RequestDispatcher getNamedDispatcher(String name) { throw new UnsupportedOperationException(); }
    @Override
    public Servlet getServlet(String name) { throw new UnsupportedOperationException(); }
    @Override
    public Enumeration<Servlet> getServlets() { throw new UnsupportedOperationException(); }
    @Override
    public Enumeration<String> getServletNames() { throw new UnsupportedOperationException(); }
    @Override
    public void log(String message) { throw new UnsupportedOperationException(); }
    @Override
    public void log(Exception exception, String message) { throw new UnsupportedOperationException(); }
    @Override
    public void log(String message, Throwable throwable) { throw new UnsupportedOperationException(); }
    @Override
    public String getRealPath(String path) { throw new UnsupportedOperationException(); }
    @Override
    public String getServerInfo() { throw new UnsupportedOperationException(); }
    @Override
    public String getServletContextName() { return "cache-webapp"; }
}
