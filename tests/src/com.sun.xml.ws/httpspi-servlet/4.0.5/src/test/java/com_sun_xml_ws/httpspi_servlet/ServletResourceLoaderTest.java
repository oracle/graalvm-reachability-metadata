/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.httpspi_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

import com.sun.xml.ws.transport.httpspi.servlet.ServletResourceLoader;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ServletResourceLoaderTest {
    @Test
    @Timeout(60)
    void resolvesServletResourcesCatalogAndResourcePaths() throws MalformedURLException {
        URL serviceDescriptor = new URL("file:/WEB-INF/sun-jaxws.xml");
        URL catalog = new URL("file:/WEB-INF/jax-ws-catalog.xml");
        RecordingServletContext context = new RecordingServletContext(
                Map.of(
                        "/WEB-INF/sun-jaxws.xml", serviceDescriptor,
                        "/WEB-INF/jax-ws-catalog.xml", catalog),
                Set.of("/WEB-INF/sun-jaxws.xml", "/WEB-INF/jax-ws-catalog.xml"));
        ServletResourceLoader loader = new ServletResourceLoader(context);

        assertThat(loader.getResource("/WEB-INF/sun-jaxws.xml")).isSameAs(serviceDescriptor);
        assertThat(loader.getCatalogFile()).isSameAs(catalog);
        assertThat(loader.getResourcePaths("/WEB-INF"))
                .containsExactlyInAnyOrder(
                        "/WEB-INF/sun-jaxws.xml", "/WEB-INF/jax-ws-catalog.xml");
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Not used by this resource loader test");
    }

    private static final class RecordingServletContext implements ServletContext {
        private final Map<String, URL> resources;
        private final Set<String> resourcePaths;

        private RecordingServletContext(Map<String, URL> resources, Set<String> resourcePaths) {
            this.resources = resources;
            this.resourcePaths = resourcePaths;
        }

        @Override
        public URL getResource(final String path) {
            return resources.get(path);
        }

        @Override
        public Set<String> getResourcePaths(final String path) {
            return resourcePaths;
        }

        @Override
        public <T extends EventListener> void addListener(final T eventListener) {
            throw unsupported();
        }

        @Override
        public String getContextPath() {
            throw unsupported();
        }

        @Override
        public ServletContext getContext(final String uripath) {
            throw unsupported();
        }

        @Override
        public int getMajorVersion() {
            throw unsupported();
        }

        @Override
        public int getMinorVersion() {
            throw unsupported();
        }

        @Override
        public int getEffectiveMajorVersion() {
            throw unsupported();
        }

        @Override
        public int getEffectiveMinorVersion() {
            throw unsupported();
        }

        @Override
        public String getMimeType(final String file) {
            throw unsupported();
        }

        @Override
        public InputStream getResourceAsStream(final String path) {
            throw unsupported();
        }

        @Override
        public RequestDispatcher getRequestDispatcher(final String path) {
            throw unsupported();
        }

        @Override
        public RequestDispatcher getNamedDispatcher(final String name) {
            throw unsupported();
        }

        @Override
        public void log(final String message) {
            throw unsupported();
        }

        @Override
        public void log(final String message, final Throwable throwable) {
            throw unsupported();
        }

        @Override
        public String getRealPath(final String path) {
            throw unsupported();
        }

        @Override
        public String getServerInfo() {
            throw unsupported();
        }

        @Override
        public String getInitParameter(final String name) {
            throw unsupported();
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            throw unsupported();
        }

        @Override
        public boolean setInitParameter(final String name, final String value) {
            throw unsupported();
        }

        @Override
        public Object getAttribute(final String name) {
            throw unsupported();
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            throw unsupported();
        }

        @Override
        public void setAttribute(final String name, final Object object) {
            throw unsupported();
        }

        @Override
        public void removeAttribute(final String name) {
            throw unsupported();
        }

        @Override
        public String getServletContextName() {
            throw unsupported();
        }

        @Override
        public ServletRegistration.Dynamic addServlet(final String name, final String className) {
            throw unsupported();
        }

        @Override
        public ServletRegistration.Dynamic addServlet(final String name, final Servlet servlet) {
            throw unsupported();
        }

        @Override
        public ServletRegistration.Dynamic addServlet(
                final String name, final Class<? extends Servlet> servletClass) {
            throw unsupported();
        }

        @Override
        public ServletRegistration.Dynamic addJspFile(final String servletName, final String jspFile) {
            throw unsupported();
        }

        @Override
        public <T extends Servlet> T createServlet(final Class<T> servletClass) throws ServletException {
            throw unsupported();
        }

        @Override
        public ServletRegistration getServletRegistration(final String servletName) {
            throw unsupported();
        }

        @Override
        public Map<String, ? extends ServletRegistration> getServletRegistrations() {
            throw unsupported();
        }

        @Override
        public FilterRegistration.Dynamic addFilter(final String name, final String className) {
            throw unsupported();
        }

        @Override
        public FilterRegistration.Dynamic addFilter(final String name, final Filter filter) {
            throw unsupported();
        }

        @Override
        public FilterRegistration.Dynamic addFilter(
                final String name, final Class<? extends Filter> filterClass) {
            throw unsupported();
        }

        @Override
        public <T extends Filter> T createFilter(final Class<T> filterClass) throws ServletException {
            throw unsupported();
        }

        @Override
        public FilterRegistration getFilterRegistration(final String filterName) {
            throw unsupported();
        }

        @Override
        public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
            throw unsupported();
        }

        @Override
        public SessionCookieConfig getSessionCookieConfig() {
            throw unsupported();
        }

        @Override
        public void setSessionTrackingModes(final Set<SessionTrackingMode> sessionTrackingModes) {
            throw unsupported();
        }

        @Override
        public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
            throw unsupported();
        }

        @Override
        public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
            throw unsupported();
        }

        @Override
        public void addListener(final String className) {
            throw unsupported();
        }

        @Override
        public void addListener(final Class<? extends EventListener> listenerClass) {
            throw unsupported();
        }

        @Override
        public <T extends EventListener> T createListener(final Class<T> listenerClass) throws ServletException {
            throw unsupported();
        }

        @Override
        public JspConfigDescriptor getJspConfigDescriptor() {
            throw unsupported();
        }

        @Override
        public ClassLoader getClassLoader() {
            throw unsupported();
        }

        @Override
        public void declareRoles(final String... roleNames) {
            throw unsupported();
        }

        @Override
        public String getVirtualServerName() {
            throw unsupported();
        }

        @Override
        public int getSessionTimeout() {
            throw unsupported();
        }

        @Override
        public void setSessionTimeout(final int sessionTimeout) {
            throw unsupported();
        }

        @Override
        public String getRequestCharacterEncoding() {
            throw unsupported();
        }

        @Override
        public void setRequestCharacterEncoding(final String encoding) {
            throw unsupported();
        }

        @Override
        public String getResponseCharacterEncoding() {
            throw unsupported();
        }

        @Override
        public void setResponseCharacterEncoding(final String encoding) {
            throw unsupported();
        }
    }
}
