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
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.tomcat.websocket.server.Constants;
import org.apache.tomcat.websocket.server.WsSci;
import org.apache.tomcat.websocket.server.WsServerContainer;
import org.junit.jupiter.api.Test;

public class WsServerContainerTest {
    private static final AtomicInteger ENCODER_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger CONFIGURATOR_CONSTRUCTIONS = new AtomicInteger();

    @Test
    void annotatedEndpointRegistrationInstantiatesEncoderAndConfigurator() throws ServletException {
        ENCODER_CONSTRUCTIONS.set(0);
        CONFIGURATOR_CONSTRUCTIONS.set(0);
        TestServletContext servletContext = new TestServletContext();

        new WsSci().onStartup(Set.of(AnnotatedEndpoint.class), servletContext);

        assertThat(servletContext.getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE))
                .isInstanceOf(WsServerContainer.class);
        assertThat(ENCODER_CONSTRUCTIONS).hasValue(1);
        assertThat(CONFIGURATOR_CONSTRUCTIONS).hasValue(1);
    }

    @ServerEndpoint(value = "/configured", encoders = TextEncoder.class, configurator = CustomConfigurator.class)
    public static class AnnotatedEndpoint {
    }

    public static class TextEncoder implements Encoder.Text<String> {
        public TextEncoder() {
            ENCODER_CONSTRUCTIONS.incrementAndGet();
        }

        @Override
        public String encode(String object) throws EncodeException {
            return object;
        }

        @Override
        public void init(EndpointConfig config) {
        }

        @Override
        public void destroy() {
        }
    }

    public static class CustomConfigurator extends ServerEndpointConfig.Configurator {
        public CustomConfigurator() {
            CONFIGURATOR_CONSTRUCTIONS.incrementAndGet();
        }
    }

    private static class TestServletContext implements ServletContext {
        private final Map<String, Object> attributes = new HashMap<>();
        private final Map<String, String> initParameters = new HashMap<>();

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
        @Deprecated
        public Servlet getServlet(String name) throws ServletException {
            return null;
        }

        @Override
        @Deprecated
        public Enumeration<Servlet> getServlets() {
            return Collections.emptyEnumeration();
        }

        @Override
        @Deprecated
        public Enumeration<String> getServletNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public void log(String msg) {
        }

        @Override
        @Deprecated
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
            return initParameters.get(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(initParameters.keySet());
        }

        @Override
        public boolean setInitParameter(String name, String value) {
            if (initParameters.containsKey(name)) {
                return false;
            }
            initParameters.put(name, value);
            return true;
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
            throw new UnsupportedOperationException();
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
            return null;
        }

        @Override
        public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
            return null;
        }

        @Override
        public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
            return null;
        }

        @Override
        public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
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
}
