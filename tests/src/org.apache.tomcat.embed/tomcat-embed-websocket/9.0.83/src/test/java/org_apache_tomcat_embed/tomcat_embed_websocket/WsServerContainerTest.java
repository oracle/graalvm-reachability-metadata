/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.tomcat.websocket.server.Constants;
import org.apache.tomcat.websocket.server.WsSci;
import org.apache.tomcat.websocket.server.WsServerContainer;
import org.junit.jupiter.api.Test;

public class WsServerContainerTest {

    @Test
    public void startupRegistersAnnotatedEndpointWithCustomConfiguratorAndEncoder() throws Exception {
        TestConfigurator.constructed = false;
        TestEncoder.constructed = false;
        TestServletContext servletContext = new TestServletContext();

        new WsSci().onStartup(Collections.singleton(AnnotatedEndpoint.class), servletContext);

        assertThat(TestConfigurator.constructed).isTrue();
        assertThat(TestEncoder.constructed).isTrue();
        Object container = servletContext.getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
        assertThat(container).isInstanceOf(WsServerContainer.class);
    }

    @ServerEndpoint(value = "/ws/{name}", configurator = TestConfigurator.class, encoders = TestEncoder.class)
    public static class AnnotatedEndpoint {
        @OnOpen
        public void onOpen(Session session) {
        }
    }

    public static class TestConfigurator extends ServerEndpointConfig.Configurator {
        private static boolean constructed;

        public TestConfigurator() {
            constructed = true;
        }
    }

    public static class TestEncoder implements Encoder.Text<String> {
        private static boolean constructed;

        public TestEncoder() {
            constructed = true;
        }

        @Override
        public void init(EndpointConfig config) {
        }

        @Override
        public void destroy() {
        }

        @Override
        public String encode(String object) throws EncodeException {
            return object;
        }
    }

    private static class TestServletContext implements ServletContext {
        private final Map<String, Object> attributes = new HashMap<>();

        @Override
        public String getContextPath() {
            return "/test";
        }

        @Override
        public ServletContext getContext(String uripath) {
            return null;
        }

        @Override
        public int getMajorVersion() {
            return 4;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public int getEffectiveMajorVersion() {
            return 4;
        }

        @Override
        public int getEffectiveMinorVersion() {
            return 0;
        }

        @Override
        public String getMimeType(String file) {
            return null;
        }

        @Override
        public Set<String> getResourcePaths(String path) {
            return Collections.emptySet();
        }

        @Override
        public URL getResource(String path) throws MalformedURLException {
            return null;
        }

        @Override
        public InputStream getResourceAsStream(String path) {
            return null;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            return null;
        }

        @Override
        public RequestDispatcher getNamedDispatcher(String name) {
            return null;
        }

        @Override
        public Servlet getServlet(String name) throws ServletException {
            return null;
        }

        @Override
        public Enumeration<Servlet> getServlets() {
            return Collections.emptyEnumeration();
        }

        @Override
        public Enumeration<String> getServletNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public void log(String msg) {
        }

        @Override
        public void log(Exception exception, String msg) {
        }

        @Override
        public void log(String message, Throwable throwable) {
        }

        @Override
        public String getRealPath(String path) {
            return null;
        }

        @Override
        public String getServerInfo() {
            return "test";
        }

        @Override
        public String getInitParameter(String name) {
            return null;
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public boolean setInitParameter(String name, String value) {
            return false;
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        @Override
        public void setAttribute(String name, Object object) {
            attributes.put(name, object);
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public String getServletContextName() {
            return "test";
        }

        @Override
        public ServletRegistration.Dynamic addServlet(String servletName, String className) {
            return null;
        }

        @Override
        public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
            return null;
        }

        @Override
        public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
            return null;
        }

        @Override
        public ServletRegistration.Dynamic addJspFile(String servletName, String jspFile) {
            return null;
        }

        @Override
        public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
            throw new ServletException("Servlet creation is not supported by this test context");
        }

        @Override
        public ServletRegistration getServletRegistration(String servletName) {
            return null;
        }

        @Override
        public Map<String, ? extends ServletRegistration> getServletRegistrations() {
            return Collections.emptyMap();
        }

        @Override
        public FilterRegistration.Dynamic addFilter(String filterName, String className) {
            return new TestFilterRegistration(filterName, className);
        }

        @Override
        public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
            return new TestFilterRegistration(filterName, filter.getClass().getName());
        }

        @Override
        public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
            return new TestFilterRegistration(filterName, filterClass.getName());
        }

        @Override
        public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
            throw new ServletException("Filter creation is not supported by this test context");
        }

        @Override
        public FilterRegistration getFilterRegistration(String filterName) {
            return null;
        }

        @Override
        public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
            return Collections.emptyMap();
        }

        @Override
        public SessionCookieConfig getSessionCookieConfig() {
            return null;
        }

        @Override
        public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
        }

        @Override
        public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
            return Collections.emptySet();
        }

        @Override
        public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
            return Collections.emptySet();
        }

        @Override
        public void addListener(String className) {
        }

        @Override
        public <T extends EventListener> void addListener(T listener) {
        }

        @Override
        public void addListener(Class<? extends EventListener> listenerClass) {
        }

        @Override
        public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
            throw new ServletException("Listener creation is not supported by this test context");
        }

        @Override
        public JspConfigDescriptor getJspConfigDescriptor() {
            return null;
        }

        @Override
        public ClassLoader getClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }

        @Override
        public void declareRoles(String... roleNames) {
        }

        @Override
        public String getVirtualServerName() {
            return "test";
        }

        @Override
        public int getSessionTimeout() {
            return 0;
        }

        @Override
        public void setSessionTimeout(int sessionTimeout) {
        }

        @Override
        public String getRequestCharacterEncoding() {
            return null;
        }

        @Override
        public void setRequestCharacterEncoding(String encoding) {
        }

        @Override
        public String getResponseCharacterEncoding() {
            return null;
        }

        @Override
        public void setResponseCharacterEncoding(String encoding) {
        }
    }

    private static class TestFilterRegistration implements FilterRegistration.Dynamic {
        private final String name;
        private final String className;
        private boolean asyncSupported;

        TestFilterRegistration(String name, String className) {
            this.name = name;
            this.className = className;
        }

        @Override
        public void setAsyncSupported(boolean isAsyncSupported) {
            asyncSupported = isAsyncSupported;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public boolean setInitParameter(String parameterName, String parameterValue) {
            return false;
        }

        @Override
        public String getInitParameter(String parameterName) {
            return null;
        }

        @Override
        public Set<String> setInitParameters(Map<String, String> initParameters) {
            return Collections.emptySet();
        }

        @Override
        public Map<String, String> getInitParameters() {
            return Collections.emptyMap();
        }

        @Override
        public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter,
                String... servletNames) {
        }

        @Override
        public Collection<String> getServletNameMappings() {
            return Collections.emptyList();
        }

        @Override
        public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter,
                String... urlPatterns) {
            assertThat(asyncSupported).isTrue();
        }

        @Override
        public Collection<String> getUrlPatternMappings() {
            return Collections.emptyList();
        }
    }
}
