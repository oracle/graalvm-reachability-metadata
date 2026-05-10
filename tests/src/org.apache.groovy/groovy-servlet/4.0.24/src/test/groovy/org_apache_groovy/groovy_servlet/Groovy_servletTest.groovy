/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_servlet

import groovy.servlet.GroovyServlet
import groovy.servlet.ServletBinding
import groovy.servlet.ServletCategory
import groovy.servlet.TemplateServlet
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

import javax.servlet.AsyncContext
import javax.servlet.DispatcherType
import javax.servlet.Filter
import javax.servlet.FilterRegistration
import javax.servlet.RequestDispatcher
import javax.servlet.Servlet
import javax.servlet.ServletConfig
import javax.servlet.ServletContext
import javax.servlet.ServletException
import javax.servlet.ServletInputStream
import javax.servlet.ServletOutputStream
import javax.servlet.ServletRegistration
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.SessionCookieConfig
import javax.servlet.SessionTrackingMode
import javax.servlet.WriteListener
import javax.servlet.descriptor.JspConfigDescriptor
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession
import javax.servlet.http.HttpSessionContext
import javax.servlet.http.HttpUpgradeHandler
import javax.servlet.http.Part
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.Principal

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

public class Groovy_servletTest {
    @Test
    void servletBindingExposesRequestDataLazyOutputAndDispatchHelpers() {
        TestServletContext context = new TestServletContext(Files.createTempDirectory('groovy-servlet-context'))
        TestHttpSession session = new TestHttpSession(context)
        TestHttpServletRequest request = new TestHttpServletRequest(context, session)
        request.parameters.put('name', ['Ada'] as String[])
        request.parameters.put('multi', ['one', 'two'] as String[])
        request.parameters.put('request', ['reserved'] as String[])
        request.headers.put('X-Test', ['header-value'])
        TestHttpServletResponse response = new TestHttpServletResponse()

        ServletBinding binding = new ServletBinding(request, response, context)

        assertThat(binding.getVariable('request')).isSameAs(request)
        assertThat(binding.getVariable('response')).isSameAs(response)
        assertThat(binding.getVariable('context')).isSameAs(context)
        assertThat(binding.getVariable('application')).isSameAs(context)
        assertThat(binding.getVariable('session')).isSameAs(session)
        Map params = binding.getVariable('params') as Map
        assertThat(params).containsEntry('name', 'Ada')
        assertThat(params['multi'] as String[]).containsExactly('one', 'two')
        assertThat(params).doesNotContainKey('request')
        assertThat(binding.getVariable('headers')).containsEntry('X-Test', 'header-value')
        assertThat(binding.getVariables()).containsKeys('out', 'sout', 'html', 'forward', 'include', 'redirect')

        PrintWriter out = binding.getVariable('out') as PrintWriter
        out.print('writer-output')
        out.flush()
        assertThat(response.body).isEqualTo('writer-output')
        assertThatThrownBy { (binding.getVariable('sout') as ServletOutputStream).write(33) }
                .isInstanceOf(IllegalStateException)

        binding.setVariable('custom', 42)
        assertThat(binding.getVariable('custom')).isEqualTo(42)
        assertThatThrownBy { binding.setVariable('out', 'reserved') }
                .isInstanceOf(IllegalArgumentException)
        assertThatThrownBy { binding.getVariable('') }
                .isInstanceOf(IllegalArgumentException)

        (binding.getVariable('forward') as Closure).call('/target')
        (binding.getVariable('include') as Closure).call('/fragment')
        binding.redirect('/next')

        assertThat(request.dispatchers['/target'].forwarded).isTrue()
        assertThat(request.dispatchers['/fragment'].included).isTrue()
        assertThat(response.redirectLocation).isEqualTo('/next')
    }

    @Test
    void servletCategoryProvidesGroovyAttributeAccessForServletScopes() {
        TestServletContext context = new TestServletContext(Files.createTempDirectory('groovy-servlet-category'))
        TestHttpSession session = new TestHttpSession(context)
        TestHttpServletRequest request = new TestHttpServletRequest(context, session)

        use(ServletCategory) {
            context['applicationName'] = 'groovy'
            session['userName'] = 'trillian'
            request['traceId'] = 'trace-123'

            assertThat(context['applicationName']).isEqualTo('groovy')
            assertThat(session['userName']).isEqualTo('trillian')
            assertThat(request['traceId']).isEqualTo('trace-123')
        }
    }

    @Test
    void templateServletRendersTemplatesWithRequestHeadersParametersAndCustomVariables() {
        try {
            Path root = Files.createTempDirectory('groovy-template-servlet')
            Files.writeString(root.resolve('hello.tpl'), 'Hello ${params.name}; ${headers["X-Test"]}; ${extra}', StandardCharsets.UTF_8)
            TestServletContext context = new TestServletContext(root)
            TestTemplateServlet servlet = new TestTemplateServlet()
            servlet.init(new TestServletConfig(context, [
                    'generated.by': 'false',
                    'encoding'    : 'UTF-8'
            ]))
            TestHttpServletRequest request = new TestHttpServletRequest(context, new TestHttpSession(context))
            request.servletPath = '/hello.tpl'
            request.parameters.put('name', ['Grace'] as String[])
            request.headers.put('X-Test', ['header'])
            TestHttpServletResponse response = new TestHttpServletResponse()

            servlet.service(request, response)

            assertThat(response.status).isEqualTo(HttpServletResponse.SC_OK)
            assertThat(response.contentType).isEqualTo('text/html; charset=UTF-8')
            assertThat(response.body).isEqualTo('Hello Grace; header; from-setVariables')
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error)
        }
    }

    @Test
    void groovyServletRunsGroovletScriptsWithServletBindingAndCategorySupport() {
        try {
            Path root = Files.createTempDirectory('groovy-groovlet-servlet')
            Files.writeString(root.resolve('hello.groovy'), '''
response.setHeader('X-Script', 'ran')
application['scriptAttribute'] = 'stored-in-context'
session['visited'] = params.name
out.print("Hello ${params.name} ${headers['X-Test']}")
''', StandardCharsets.UTF_8)
            TestServletContext context = new TestServletContext(root)
            GroovyServlet servlet = new GroovyServlet()
            servlet.init(new TestServletConfig(context, [:]))
            TestHttpSession session = new TestHttpSession(context)
            TestHttpServletRequest request = new TestHttpServletRequest(context, session)
            request.servletPath = '/hello.groovy'
            request.parameters.put('name', ['Linus'] as String[])
            request.headers.put('X-Test', ['header'])
            TestHttpServletResponse response = new TestHttpServletResponse()

            servlet.service(request, response)

            assertThat(response.contentType).isEqualTo('text/html; charset=UTF-8')
            assertThat(response.getHeader('X-Script')).isEqualTo('ran')
            assertThat(response.body).isEqualTo('Hello Linus header')
            assertThat(context.getAttribute('scriptAttribute')).isEqualTo('stored-in-context')
            assertThat(session.getAttribute('visited')).isEqualTo('Linus')
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error)
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error
        }
    }
}

class TestTemplateServlet extends TemplateServlet {
    @Override
    protected void setVariables(ServletBinding binding) {
        binding.setVariable('extra', 'from-setVariables')
    }
}

class TestServletConfig implements ServletConfig {
    final ServletContext servletContext
    final Map<String, String> initParameters

    TestServletConfig(ServletContext servletContext, Map<String, String> initParameters) {
        this.servletContext = servletContext
        this.initParameters = initParameters
    }

    @Override
    String getServletName() {
        'test-servlet'
    }

    @Override
    ServletContext getServletContext() {
        servletContext
    }

    @Override
    String getInitParameter(String name) {
        initParameters[name]
    }

    @Override
    Enumeration<String> getInitParameterNames() {
        Collections.enumeration(initParameters.keySet())
    }
}

class TestServletContext implements ServletContext {
    final Path root
    final Map<String, Object> attributes = [:]
    final Map<String, String> initParameters = [:]
    final List<String> logMessages = []

    TestServletContext(Path root) {
        this.root = root
    }

    @Override
    String getContextPath() { '/test' }

    @Override
    ServletContext getContext(String uripath) { this }

    @Override
    int getMajorVersion() { 4 }

    @Override
    int getMinorVersion() { 0 }

    @Override
    int getEffectiveMajorVersion() { 4 }

    @Override
    int getEffectiveMinorVersion() { 0 }

    @Override
    String getMimeType(String file) { file.endsWith('.html') ? 'text/html' : 'text/plain' }

    @Override
    Set<String> getResourcePaths(String path) {
        Path directory = resolve(path)
        if (!Files.isDirectory(directory)) {
            return null
        }
        Set<String> paths = [] as Set
        Files.list(directory).withCloseable { stream ->
            stream.each { Path child -> paths.add(path + child.fileName.toString()) }
        }
        paths
    }

    @Override
    URL getResource(String path) throws MalformedURLException {
        Path resolved = resolve(path)
        Files.exists(resolved) ? resolved.toUri().toURL() : null
    }

    @Override
    InputStream getResourceAsStream(String path) {
        Path resolved = resolve(path)
        Files.exists(resolved) ? Files.newInputStream(resolved) : null
    }

    @Override
    RequestDispatcher getRequestDispatcher(String path) { new TestRequestDispatcher(path) }

    @Override
    RequestDispatcher getNamedDispatcher(String name) { new TestRequestDispatcher(name) }

    @Override
    Servlet getServlet(String name) throws ServletException { null }

    @Override
    Enumeration<Servlet> getServlets() { Collections.emptyEnumeration() }

    @Override
    Enumeration<String> getServletNames() { Collections.emptyEnumeration() }

    @Override
    void log(String msg) { logMessages.add(msg) }

    @Override
    void log(Exception exception, String msg) { logMessages.add(msg + ': ' + exception.message) }

    @Override
    void log(String message, Throwable throwable) { logMessages.add(message + ': ' + throwable.message) }

    @Override
    String getRealPath(String path) { resolve(path).toAbsolutePath().toString() }

    @Override
    String getServerInfo() { 'test-server' }

    @Override
    String getInitParameter(String name) { initParameters[name] }

    @Override
    Enumeration<String> getInitParameterNames() { Collections.enumeration(initParameters.keySet()) }

    @Override
    boolean setInitParameter(String name, String value) { initParameters.put(name, value) == null }

    @Override
    Object getAttribute(String name) { attributes[name] }

    @Override
    Enumeration<String> getAttributeNames() { Collections.enumeration(attributes.keySet()) }

    @Override
    void setAttribute(String name, Object object) { attributes[name] = object }

    @Override
    void removeAttribute(String name) { attributes.remove(name) }

    @Override
    String getServletContextName() { 'test-context' }

    @Override
    ServletRegistration.Dynamic addServlet(String servletName, String className) { null }

    @Override
    ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) { null }

    @Override
    ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) { null }

    @Override
    ServletRegistration.Dynamic addJspFile(String servletName, String jspFile) { null }

    @Override
    <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException { null }

    @Override
    ServletRegistration getServletRegistration(String servletName) { null }

    @Override
    Map<String, ? extends ServletRegistration> getServletRegistrations() { [:] }

    @Override
    FilterRegistration.Dynamic addFilter(String filterName, String className) { null }

    @Override
    FilterRegistration.Dynamic addFilter(String filterName, Filter filter) { null }

    @Override
    FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) { null }

    @Override
    <T extends Filter> T createFilter(Class<T> clazz) throws ServletException { null }

    @Override
    FilterRegistration getFilterRegistration(String filterName) { null }

    @Override
    Map<String, ? extends FilterRegistration> getFilterRegistrations() { [:] }

    @Override
    SessionCookieConfig getSessionCookieConfig() { null }

    @Override
    void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) { }

    @Override
    Set<SessionTrackingMode> getDefaultSessionTrackingModes() { [] as Set }

    @Override
    Set<SessionTrackingMode> getEffectiveSessionTrackingModes() { [] as Set }

    @Override
    void addListener(String className) { }

    @Override
    <T extends EventListener> void addListener(T t) { }

    @Override
    void addListener(Class<? extends EventListener> listenerClass) { }

    @Override
    <T extends EventListener> T createListener(Class<T> clazz) throws ServletException { null }

    @Override
    JspConfigDescriptor getJspConfigDescriptor() { null }

    @Override
    ClassLoader getClassLoader() { Thread.currentThread().contextClassLoader }

    @Override
    void declareRoles(String... roleNames) { }

    @Override
    String getVirtualServerName() { 'test-vhost' }

    @Override
    int getSessionTimeout() { 30 }

    @Override
    void setSessionTimeout(int sessionTimeout) { }

    @Override
    String getRequestCharacterEncoding() { 'UTF-8' }

    @Override
    void setRequestCharacterEncoding(String encoding) { }

    @Override
    String getResponseCharacterEncoding() { 'UTF-8' }

    @Override
    void setResponseCharacterEncoding(String encoding) { }

    private Path resolve(String path) {
        String relative = path == '/' ? '' : path.replaceFirst('^/', '')
        root.resolve(relative).normalize()
    }
}

class TestHttpServletRequest implements HttpServletRequest {
    final ServletContext servletContext
    final HttpSession session
    final Map<String, Object> attributes = [:]
    final Map<String, String[]> parameters = [:]
    final Map<String, List<String>> headers = [:]
    final Map<String, TestRequestDispatcher> dispatchers = [:]
    String servletPath = '/index.groovy'
    String pathInfo

    TestHttpServletRequest(ServletContext servletContext, HttpSession session) {
        this.servletContext = servletContext
        this.session = session
    }

    @Override
    Object getAttribute(String name) { attributes[name] }

    @Override
    Enumeration<String> getAttributeNames() { Collections.enumeration(attributes.keySet()) }

    @Override
    String getCharacterEncoding() { 'UTF-8' }

    @Override
    void setCharacterEncoding(String env) throws UnsupportedEncodingException { }

    @Override
    int getContentLength() { 0 }

    @Override
    long getContentLengthLong() { 0L }

    @Override
    String getContentType() { null }

    @Override
    ServletInputStream getInputStream() throws IOException { null }

    @Override
    String getParameter(String name) { parameters[name]?.first() }

    @Override
    Enumeration<String> getParameterNames() { Collections.enumeration(parameters.keySet()) }

    @Override
    String[] getParameterValues(String name) { parameters[name] }

    @Override
    Map<String, String[]> getParameterMap() { parameters }

    @Override
    String getProtocol() { 'HTTP/1.1' }

    @Override
    String getScheme() { 'http' }

    @Override
    String getServerName() { 'localhost' }

    @Override
    int getServerPort() { 80 }

    @Override
    BufferedReader getReader() throws IOException { new BufferedReader(new StringReader('')) }

    @Override
    String getRemoteAddr() { '127.0.0.1' }

    @Override
    String getRemoteHost() { 'localhost' }

    @Override
    void setAttribute(String name, Object value) { attributes[name] = value }

    @Override
    void removeAttribute(String name) { attributes.remove(name) }

    @Override
    Locale getLocale() { Locale.US }

    @Override
    Enumeration<Locale> getLocales() { Collections.enumeration([Locale.US]) }

    @Override
    boolean isSecure() { false }

    @Override
    RequestDispatcher getRequestDispatcher(String path) {
        dispatchers.computeIfAbsent(path) { String key -> new TestRequestDispatcher(key) }
    }

    @Override
    String getRealPath(String path) { servletContext.getRealPath(path) }

    @Override
    int getRemotePort() { 12345 }

    @Override
    String getLocalName() { 'localhost' }

    @Override
    String getLocalAddr() { '127.0.0.1' }

    @Override
    int getLocalPort() { 80 }

    @Override
    ServletContext getServletContext() { servletContext }

    @Override
    AsyncContext startAsync() throws IllegalStateException { null }

    @Override
    AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException { null }

    @Override
    boolean isAsyncStarted() { false }

    @Override
    boolean isAsyncSupported() { false }

    @Override
    AsyncContext getAsyncContext() { null }

    @Override
    DispatcherType getDispatcherType() { DispatcherType.REQUEST }

    @Override
    String getAuthType() { null }

    @Override
    Cookie[] getCookies() { [] as Cookie[] }

    @Override
    long getDateHeader(String name) { -1L }

    @Override
    String getHeader(String name) { headers[name]?.first() }

    @Override
    Enumeration<String> getHeaders(String name) { Collections.enumeration(headers[name] ?: []) }

    @Override
    Enumeration<String> getHeaderNames() { Collections.enumeration(headers.keySet()) }

    @Override
    int getIntHeader(String name) { getHeader(name) == null ? -1 : getHeader(name).toInteger() }

    @Override
    String getMethod() { 'GET' }

    @Override
    String getPathInfo() { pathInfo }

    @Override
    String getPathTranslated() { pathInfo == null ? null : servletContext.getRealPath(pathInfo) }

    @Override
    String getContextPath() { servletContext.contextPath }

    @Override
    String getQueryString() { null }

    @Override
    String getRemoteUser() { null }

    @Override
    boolean isUserInRole(String role) { false }

    @Override
    Principal getUserPrincipal() { null }

    @Override
    String getRequestedSessionId() { session?.id }

    @Override
    String getRequestURI() { servletPath + (pathInfo ?: '') }

    @Override
    StringBuffer getRequestURL() { new StringBuffer('http://localhost' + getRequestURI()) }

    @Override
    String getServletPath() { servletPath }

    @Override
    HttpSession getSession(boolean create) { session }

    @Override
    HttpSession getSession() { session }

    @Override
    String changeSessionId() { session.id }

    @Override
    boolean isRequestedSessionIdValid() { session != null }

    @Override
    boolean isRequestedSessionIdFromCookie() { true }

    @Override
    boolean isRequestedSessionIdFromURL() { false }

    @Override
    boolean isRequestedSessionIdFromUrl() { false }

    @Override
    boolean authenticate(HttpServletResponse response) throws IOException, ServletException { false }

    @Override
    void login(String username, String password) throws ServletException { }

    @Override
    void logout() throws ServletException { }

    @Override
    Collection<Part> getParts() throws IOException, ServletException { [] }

    @Override
    Part getPart(String name) throws IOException, ServletException { null }

    @Override
    <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException { null }
}

class TestHttpServletResponse implements HttpServletResponse {
    final StringWriter bodyWriter = new StringWriter()
    final TestServletOutputStream servletOutputStream = new TestServletOutputStream()
    final Map<String, List<String>> headers = [:].withDefault { [] }
    String characterEncoding = 'UTF-8'
    String contentType
    int status = HttpServletResponse.SC_OK
    String redirectLocation
    Locale locale = Locale.US
    boolean committed

    String getBody() {
        bodyWriter.toString() + servletOutputStream.toString()
    }

    @Override
    void addCookie(Cookie cookie) { }

    @Override
    boolean containsHeader(String name) { headers.containsKey(name) }

    @Override
    String encodeURL(String url) { url }

    @Override
    String encodeRedirectURL(String url) { url }

    @Override
    String encodeUrl(String url) { url }

    @Override
    String encodeRedirectUrl(String url) { url }

    @Override
    void sendError(int sc, String msg) throws IOException { status = sc; committed = true }

    @Override
    void sendError(int sc) throws IOException { status = sc; committed = true }

    @Override
    void sendRedirect(String location) throws IOException { redirectLocation = location; status = SC_FOUND; committed = true }

    @Override
    void setDateHeader(String name, long date) { setHeader(name, Long.toString(date)) }

    @Override
    void addDateHeader(String name, long date) { addHeader(name, Long.toString(date)) }

    @Override
    void setHeader(String name, String value) { headers[name] = [value] }

    @Override
    void addHeader(String name, String value) { headers[name].add(value) }

    @Override
    void setIntHeader(String name, int value) { setHeader(name, Integer.toString(value)) }

    @Override
    void addIntHeader(String name, int value) { addHeader(name, Integer.toString(value)) }

    @Override
    void setStatus(int sc) { status = sc }

    @Override
    void setStatus(int sc, String sm) { status = sc }

    @Override
    int getStatus() { status }

    @Override
    String getHeader(String name) { headers[name]?.first() }

    @Override
    Collection<String> getHeaders(String name) { headers[name] ?: [] }

    @Override
    Collection<String> getHeaderNames() { headers.keySet() }

    @Override
    String getCharacterEncoding() { characterEncoding }

    @Override
    String getContentType() { contentType }

    @Override
    ServletOutputStream getOutputStream() throws IOException { servletOutputStream }

    @Override
    PrintWriter getWriter() throws IOException { new PrintWriter(bodyWriter) }

    @Override
    void setCharacterEncoding(String charset) { characterEncoding = charset }

    @Override
    void setContentLength(int len) { }

    @Override
    void setContentLengthLong(long len) { }

    @Override
    void setContentType(String type) { contentType = type }

    @Override
    void setBufferSize(int size) { }

    @Override
    int getBufferSize() { 0 }

    @Override
    void flushBuffer() throws IOException { committed = true }

    @Override
    void resetBuffer() { bodyWriter.buffer.setLength(0); servletOutputStream.reset() }

    @Override
    boolean isCommitted() { committed }

    @Override
    void reset() { resetBuffer(); headers.clear(); status = SC_OK; committed = false }

    @Override
    void setLocale(Locale loc) { locale = loc }

    @Override
    Locale getLocale() { locale }
}

class TestServletOutputStream extends ServletOutputStream {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream()

    @Override
    boolean isReady() { true }

    @Override
    void setWriteListener(WriteListener writeListener) { }

    @Override
    void write(int value) throws IOException { bytes.write(value) }

    void reset() { bytes.reset() }

    @Override
    String toString() { bytes.toString(StandardCharsets.UTF_8.name()) }
}

class TestRequestDispatcher implements RequestDispatcher {
    final String path
    boolean forwarded
    boolean included

    TestRequestDispatcher(String path) {
        this.path = path
    }

    @Override
    void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException { forwarded = true }

    @Override
    void include(ServletRequest request, ServletResponse response) throws ServletException, IOException { included = true }
}

class TestHttpSession implements HttpSession {
    final ServletContext servletContext
    final Map<String, Object> attributes = [:]
    final String id = UUID.randomUUID().toString()
    final long creationTime = System.currentTimeMillis()
    int maxInactiveInterval = 1800
    boolean valid = true

    TestHttpSession(ServletContext servletContext) {
        this.servletContext = servletContext
    }

    @Override
    long getCreationTime() { creationTime }

    @Override
    String getId() { id }

    @Override
    long getLastAccessedTime() { creationTime }

    @Override
    ServletContext getServletContext() { servletContext }

    @Override
    void setMaxInactiveInterval(int interval) { maxInactiveInterval = interval }

    @Override
    int getMaxInactiveInterval() { maxInactiveInterval }

    @Override
    HttpSessionContext getSessionContext() { null }

    @Override
    Object getAttribute(String name) { attributes[name] }

    @Override
    Object getValue(String name) { getAttribute(name) }

    @Override
    Enumeration<String> getAttributeNames() { Collections.enumeration(attributes.keySet()) }

    @Override
    String[] getValueNames() { attributes.keySet() as String[] }

    @Override
    void setAttribute(String name, Object value) { attributes[name] = value }

    @Override
    void putValue(String name, Object value) { setAttribute(name, value) }

    @Override
    void removeAttribute(String name) { attributes.remove(name) }

    @Override
    void removeValue(String name) { removeAttribute(name) }

    @Override
    void invalidate() { valid = false }

    @Override
    boolean isNew() { false }
}
