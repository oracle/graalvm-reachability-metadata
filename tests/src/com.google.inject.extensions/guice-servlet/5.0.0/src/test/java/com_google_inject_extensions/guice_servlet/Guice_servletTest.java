/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject_extensions.guice_servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.InstanceFilterBinding;
import com.google.inject.servlet.InstanceServletBinding;
import com.google.inject.servlet.LinkedFilterBinding;
import com.google.inject.servlet.LinkedServletBinding;
import com.google.inject.servlet.ServletModule;
import com.google.inject.servlet.ServletModuleBinding;
import com.google.inject.servlet.ServletModuleTargetVisitor;
import com.google.inject.servlet.ServletScopes;
import com.google.inject.servlet.UriPatternType;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.Message;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import org.junit.jupiter.api.Test;

public class Guice_servletTest {
    private static final Key<String> REQUEST_USER_KEY = Key.get(String.class, Names.named("requestUser"));

    @Test
    void servletModuleExposesLinkedAndInstanceBindingsWithMatchingRules() {
        List<ServletModuleBinding> bindings = servletModuleBindings(new RoutingModule());

        assertThat(bindings).hasSize(7);

        LinkedFilterBinding apiFilter = binding(bindings, LinkedFilterBinding.class, "/api/*");
        assertThat(apiFilter.getUriPatternType()).isEqualTo(UriPatternType.SERVLET);
        assertThat(apiFilter.getLinkedKey()).isEqualTo(Key.get(ApiFilter.class));
        assertThat(apiFilter.getInitParams()).containsEntry("filter", "api");
        assertThat(apiFilter.matchesUri("/api/customers")).isTrue();
        assertThat(apiFilter.matchesUri("/admin/42")).isFalse();

        LinkedFilterBinding htmlFilter = binding(bindings, LinkedFilterBinding.class, "*.html");
        assertThat(htmlFilter.matchesUri("/index.html")).isTrue();
        assertThat(htmlFilter.matchesUri("/index.json")).isFalse();

        InstanceFilterBinding adminFilter = binding(bindings, InstanceFilterBinding.class, "^/admin/[0-9]+$");
        assertThat(adminFilter.getUriPatternType()).isEqualTo(UriPatternType.REGEX);
        assertThat(adminFilter.getFilterInstance()).isSameAs(RoutingModule.ADMIN_FILTER);
        assertThat(adminFilter.getInitParams()).containsEntry("filter", "admin");
        assertThat(adminFilter.matchesUri("/admin/42")).isTrue();
        assertThat(adminFilter.matchesUri("/admin/users")).isFalse();

        LinkedServletBinding healthServlet = binding(bindings, LinkedServletBinding.class, "/health");
        assertThat(healthServlet.getLinkedKey()).isEqualTo(Key.get(HealthServlet.class));
        assertThat(healthServlet.getInitParams()).containsEntry("servlet", "health");
        assertThat(healthServlet.matchesUri("/health")).isTrue();
        assertThat(healthServlet.matchesUri("/health/check")).isFalse();

        LinkedServletBinding staticServlet = binding(bindings, LinkedServletBinding.class, "/static/*");
        assertThat(staticServlet.matchesUri("/static/app.js")).isTrue();
        assertThat(staticServlet.matchesUri("/assets/app.js")).isFalse();

        InstanceServletBinding assetServlet = binding(bindings, InstanceServletBinding.class, "^/assets/.+");
        assertThat(assetServlet.getUriPatternType()).isEqualTo(UriPatternType.REGEX);
        assertThat(assetServlet.getServletInstance()).isSameAs(RoutingModule.ASSET_SERVLET);
        assertThat(assetServlet.getInitParams()).containsEntry("servlet", "asset");
        assertThat(assetServlet.matchesUri("/assets/app.css")).isTrue();
        assertThat(assetServlet.matchesUri("/static/app.css")).isFalse();

        LinkedServletBinding namedServlet = binding(bindings, LinkedServletBinding.class, "/named/*");
        assertThat(namedServlet.getLinkedKey()).isEqualTo(Key.get(NamedServlet.class, Names.named("namedServlet")));
    }

    @Test
    void requestScopeCachesValuesAndUsesSeededRequestData() throws Exception {
        Provider<RequestScopedValue> requestScopedProvider = ServletScopes.REQUEST.scope(
                Key.get(RequestScopedValue.class), new RequestScopedValueProvider());
        Provider<String> userProvider = ServletScopes.REQUEST.scope(REQUEST_USER_KEY, new DefaultRequestUserProvider());

        assertThatThrownBy(requestScopedProvider::get).isInstanceOf(OutOfScopeException.class);

        ScopedRequestResult first = executeInRequestScope(requestScopedProvider, userProvider, "alice");
        ScopedRequestResult second = executeInRequestScope(requestScopedProvider, userProvider, "bob");

        assertThat(first.firstValue).isSameAs(first.secondValue);
        assertThat(first.user).isEqualTo("alice");
        assertThat(second.firstValue).isSameAs(second.secondValue);
        assertThat(second.user).isEqualTo("bob");
        assertThat(first.firstValue).isNotSameAs(second.firstValue);
    }

    @Test
    void requestScopeRejectsSeedValuesWithTheWrongType() {
        Map<Key<?>, Object> seedMap = new LinkedHashMap<>();
        seedMap.put(REQUEST_USER_KEY, 42);

        assertThatThrownBy(() -> ServletScopes.scopeRequest(() -> null, seedMap))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not compatible")
                .hasMessageContaining(REQUEST_USER_KEY.toString());
    }

    @Test
    void duplicateServletMappingsAreReportedByServletModuleElements() {
        List<Message> messages = Elements.getElements(new DuplicateServletMappingModule()).stream()
                .filter(Message.class::isInstance)
                .map(Message.class::cast)
                .toList();

        assertThat(messages.stream().map(Message::getMessage).toList())
                .anySatisfy(message -> assertThat(message)
                        .contains("More than one servlet was mapped")
                        .contains("/duplicate"));
    }

    @Test
    void sessionScopeCachesValuesAcrossRequestsUsingTheSameHttpSession() throws Exception {
        Provider<SessionScopedValue> sessionScopedProvider = ServletScopes.SESSION.scope(
                Key.get(SessionScopedValue.class), new SessionScopedValueProvider());
        TestHttpSession session = new TestHttpSession();
        TestHttpSession otherSession = new TestHttpSession();

        ScopedSessionResult first = executeWithSession(session, () -> new ScopedSessionResult(
                sessionScopedProvider.get(), sessionScopedProvider.get()));
        ScopedSessionResult second = executeWithSession(session, () -> new ScopedSessionResult(
                sessionScopedProvider.get(), sessionScopedProvider.get()));
        ScopedSessionResult third = executeWithSession(otherSession, () -> new ScopedSessionResult(
                sessionScopedProvider.get(), sessionScopedProvider.get()));

        assertThat(first.firstValue).isSameAs(first.secondValue);
        assertThat(second.firstValue).isSameAs(first.firstValue);
        assertThat(third.firstValue).isSameAs(third.secondValue);
        assertThat(third.firstValue).isNotSameAs(first.firstValue);
    }

    @Test
    void continueRequestCreatesCallableThatUsesRequestScopeAfterServletFilterReturns() throws Exception {
        Provider<RequestScopedValue> requestScopedProvider = ServletScopes.REQUEST.scope(
                Key.get(RequestScopedValue.class), new RequestScopedValueProvider());
        Provider<String> userProvider = ServletScopes.REQUEST.scope(REQUEST_USER_KEY, new DefaultRequestUserProvider());
        Provider<SessionScopedValue> sessionScopedProvider = ServletScopes.SESSION.scope(
                Key.get(SessionScopedValue.class), new SessionScopedValueProvider());
        ContinuedRequestCapture capture = new ContinuedRequestCapture(
                requestScopedProvider, userProvider, sessionScopedProvider);

        new GuiceFilter().doFilter(
                new TestHttpServletRequest(new TestHttpSession()), new TestHttpServletResponse(), capture);

        ContinuedRequestResult result = capture.continuedRequest.call();

        assertThat(result.firstValue).isSameAs(result.secondValue);
        assertThat(result.user).isEqualTo(ContinuedRequestCapture.CONTINUED_USER);
        assertThatThrownBy(() -> capture.continuedSessionAccess.call())
                .isInstanceOf(OutOfScopeException.class)
                .hasMessageContaining("Cannot access the session");
    }

    private static ScopedRequestResult executeInRequestScope(
            Provider<RequestScopedValue> requestScopedProvider,
            Provider<String> userProvider,
            String user) throws Exception {
        Map<Key<?>, Object> seedMap = new LinkedHashMap<>();
        seedMap.put(REQUEST_USER_KEY, user);

        return ServletScopes.scopeRequest(() -> new ScopedRequestResult(
                requestScopedProvider.get(),
                requestScopedProvider.get(),
                userProvider.get()), seedMap).call();
    }

    private static List<ServletModuleBinding> servletModuleBindings(ServletModule module) {
        List<ServletModuleBinding> bindings = new ArrayList<>();
        BindingCollector collector = new BindingCollector();
        for (Element element : Elements.getElements(module)) {
            if (element instanceof Binding) {
                ServletModuleBinding servletBinding = ((Binding<?>) element).acceptTargetVisitor(collector);
                if (servletBinding != null) {
                    bindings.add(servletBinding);
                }
            }
        }
        return bindings;
    }

    private static <T extends ServletModuleBinding> T binding(
            List<ServletModuleBinding> bindings, Class<T> bindingType, String pattern) {
        return bindings.stream()
                .filter(bindingType::isInstance)
                .map(bindingType::cast)
                .filter(binding -> pattern.equals(binding.getPattern()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + bindingType.getSimpleName() + " for " + pattern));
    }

    private static final class BindingCollector extends DefaultBindingTargetVisitor<Object, ServletModuleBinding>
            implements ServletModuleTargetVisitor<Object, ServletModuleBinding> {
        @Override
        public ServletModuleBinding visit(LinkedFilterBinding binding) {
            return binding;
        }

        @Override
        public ServletModuleBinding visit(InstanceFilterBinding binding) {
            return binding;
        }

        @Override
        public ServletModuleBinding visit(LinkedServletBinding binding) {
            return binding;
        }

        @Override
        public ServletModuleBinding visit(InstanceServletBinding binding) {
            return binding;
        }
    }

    private static final class RoutingModule extends ServletModule {
        private static final Filter ADMIN_FILTER = new AdminFilter();
        private static final HttpServlet ASSET_SERVLET = new AssetServlet();

        @Override
        protected void configureServlets() {
            Map<String, String> apiFilterParams = new LinkedHashMap<>();
            apiFilterParams.put("filter", "api");
            filter("/api/*", "*.html").through(ApiFilter.class, apiFilterParams);

            Map<String, String> adminFilterParams = new LinkedHashMap<>();
            adminFilterParams.put("filter", "admin");
            filterRegex("^/admin/[0-9]+$").through(ADMIN_FILTER, adminFilterParams);

            Map<String, String> healthServletParams = new LinkedHashMap<>();
            healthServletParams.put("servlet", "health");
            serve("/health", "/static/*").with(HealthServlet.class, healthServletParams);

            Map<String, String> assetServletParams = new LinkedHashMap<>();
            assetServletParams.put("servlet", "asset");
            serveRegex("^/assets/.+").with(ASSET_SERVLET, assetServletParams);

            bind(NamedServlet.class)
                    .annotatedWith(Names.named("namedServlet"))
                    .to(NamedServlet.class)
                    .in(Singleton.class);
            serve("/named/*").with(Key.get(NamedServlet.class, Names.named("namedServlet")));
        }
    }

    private static final class DuplicateServletMappingModule extends ServletModule {
        @Override
        protected void configureServlets() {
            serve("/duplicate").with(HealthServlet.class);
            serve("/duplicate").with(AssetServlet.class);
        }
    }

    private static <T> T executeWithSession(HttpSession session, ValueSupplier<T> supplier)
            throws IOException, ServletException {
        ValueCapturingFilterChain<T> chain = new ValueCapturingFilterChain<>(supplier);
        new GuiceFilter().doFilter(new TestHttpServletRequest(session), new TestHttpServletResponse(), chain);
        return chain.value;
    }

    private interface ValueSupplier<T> {
        T get();
    }

    private static final class ValueCapturingFilterChain<T> implements FilterChain {
        private final ValueSupplier<T> supplier;
        private T value;

        private ValueCapturingFilterChain(ValueSupplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            value = supplier.get();
        }
    }

    private static final class ContinuedRequestCapture implements FilterChain {
        private static final String CONTINUED_USER = "continued-user";

        private final Provider<RequestScopedValue> requestScopedProvider;
        private final Provider<String> userProvider;
        private final Provider<SessionScopedValue> sessionScopedProvider;
        private Callable<ContinuedRequestResult> continuedRequest;
        private Callable<SessionScopedValue> continuedSessionAccess;

        private ContinuedRequestCapture(
                Provider<RequestScopedValue> requestScopedProvider,
                Provider<String> userProvider,
                Provider<SessionScopedValue> sessionScopedProvider) {
            this.requestScopedProvider = requestScopedProvider;
            this.userProvider = userProvider;
            this.sessionScopedProvider = sessionScopedProvider;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            Map<Key<?>, Object> seedMap = new LinkedHashMap<>();
            seedMap.put(REQUEST_USER_KEY, CONTINUED_USER);
            continuedRequest = ServletScopes.continueRequest(() -> new ContinuedRequestResult(
                    requestScopedProvider.get(), requestScopedProvider.get(), userProvider.get()), seedMap);
            continuedSessionAccess = ServletScopes.continueRequest(sessionScopedProvider::get, Collections.emptyMap());
        }
    }

    private static final class ContinuedRequestResult {
        private final RequestScopedValue firstValue;
        private final RequestScopedValue secondValue;
        private final String user;

        private ContinuedRequestResult(RequestScopedValue firstValue, RequestScopedValue secondValue, String user) {
            this.firstValue = firstValue;
            this.secondValue = secondValue;
            this.user = user;
        }
    }

    private static final class ScopedSessionResult {
        private final SessionScopedValue firstValue;
        private final SessionScopedValue secondValue;

        private ScopedSessionResult(SessionScopedValue firstValue, SessionScopedValue secondValue) {
            this.firstValue = firstValue;
            this.secondValue = secondValue;
        }
    }

    private static final class TestHttpSession implements HttpSession {
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        @Override
        public long getCreationTime() {
            return 0L;
        }

        @Override
        public String getId() {
            return "test-session";
        }

        @Override
        public long getLastAccessedTime() {
            return 0L;
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public void setMaxInactiveInterval(int interval) {
        }

        @Override
        public int getMaxInactiveInterval() {
            return 0;
        }

        @Override
        @SuppressWarnings("deprecation")
        public HttpSessionContext getSessionContext() {
            return null;
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public Object getValue(String name) {
            return getAttribute(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        @Override
        public String[] getValueNames() {
            return attributes.keySet().toArray(new String[0]);
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void putValue(String name, Object value) {
            setAttribute(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public void removeValue(String name) {
            removeAttribute(name);
        }

        @Override
        public void invalidate() {
            attributes.clear();
        }

        @Override
        public boolean isNew() {
            return false;
        }
    }

    private static final class TestHttpServletRequest implements HttpServletRequest {
        private final HttpSession session;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private TestHttpServletRequest(HttpSession session) {
            this.session = session;
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
            return null;
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return Collections.emptyEnumeration();
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public int getIntHeader(String name) {
            return -1;
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
            return session.getId();
        }

        @Override
        public String getRequestURI() {
            return "/session";
        }

        @Override
        public StringBuffer getRequestURL() {
            return new java.lang.StringBuffer("http://localhost/session");
        }

        @Override
        public String getServletPath() {
            return "/session";
        }

        @Override
        public HttpSession getSession(boolean create) {
            return session;
        }

        @Override
        public HttpSession getSession() {
            return session;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return true;
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
        @SuppressWarnings("deprecation")
        public boolean isRequestedSessionIdFromUrl() {
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
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public void setCharacterEncoding(String encoding) {
        }

        @Override
        public int getContentLength() {
            return 0;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public ServletInputStream getInputStream() {
            return null;
        }

        @Override
        public String getParameter(String name) {
            return null;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public String[] getParameterValues(String name) {
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
            return 80;
        }

        @Override
        public BufferedReader getReader() {
            return null;
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
        public void setAttribute(String name, Object object) {
            attributes.put(name, object);
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return Collections.enumeration(List.of(Locale.ROOT));
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
        @SuppressWarnings("deprecation")
        public String getRealPath(String path) {
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
            return 80;
        }
    }

    private static final class TestHttpServletResponse implements HttpServletResponse {
        @Override
        public void addCookie(Cookie cookie) {
        }

        @Override
        public boolean containsHeader(String name) {
            return false;
        }

        @Override
        public String encodeURL(String url) {
            return url;
        }

        @Override
        public String encodeRedirectURL(String url) {
            return url;
        }

        @Override
        @SuppressWarnings("deprecation")
        public String encodeUrl(String url) {
            return url;
        }

        @Override
        @SuppressWarnings("deprecation")
        public String encodeRedirectUrl(String url) {
            return url;
        }

        @Override
        public void sendError(int statusCode, String message) {
        }

        @Override
        public void sendError(int statusCode) {
        }

        @Override
        public void sendRedirect(String location) {
        }

        @Override
        public void setDateHeader(String name, long date) {
        }

        @Override
        public void addDateHeader(String name, long date) {
        }

        @Override
        public void setHeader(String name, String value) {
        }

        @Override
        public void addHeader(String name, String value) {
        }

        @Override
        public void setIntHeader(String name, int value) {
        }

        @Override
        public void addIntHeader(String name, int value) {
        }

        @Override
        public void setStatus(int statusCode) {
        }

        @Override
        @SuppressWarnings("deprecation")
        public void setStatus(int statusCode, String message) {
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return null;
        }

        @Override
        public PrintWriter getWriter() {
            return null;
        }

        @Override
        public void setCharacterEncoding(String encoding) {
        }

        @Override
        public void setContentLength(int length) {
        }

        @Override
        public void setContentType(String type) {
        }

        @Override
        public void setBufferSize(int size) {
        }

        @Override
        public int getBufferSize() {
            return 0;
        }

        @Override
        public void flushBuffer() {
        }

        @Override
        public void resetBuffer() {
        }

        @Override
        public boolean isCommitted() {
            return false;
        }

        @Override
        public void reset() {
        }

        @Override
        public void setLocale(Locale locale) {
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }
    }

    private static final class ScopedRequestResult {
        private final RequestScopedValue firstValue;
        private final RequestScopedValue secondValue;
        private final String user;

        private ScopedRequestResult(RequestScopedValue firstValue, RequestScopedValue secondValue, String user) {
            this.firstValue = firstValue;
            this.secondValue = secondValue;
            this.user = user;
        }
    }

    private static final class DefaultRequestUserProvider implements Provider<String> {
        @Override
        public String get() {
            return "unseeded-user";
        }
    }

    private static final class RequestScopedValueProvider implements Provider<RequestScopedValue> {
        @Override
        public RequestScopedValue get() {
            return new RequestScopedValue();
        }
    }

    private static final class SessionScopedValueProvider implements Provider<SessionScopedValue> {
        @Override
        public SessionScopedValue get() {
            return new SessionScopedValue();
        }
    }

    private static final class RequestScopedValue {
    }

    private static final class SessionScopedValue {
    }

    private static class ApiFilter implements Filter {
        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
        }
    }

    private static final class AdminFilter extends ApiFilter {
    }

    private static final class HealthServlet extends HttpServlet {
    }

    private static final class AssetServlet extends HttpServlet {
    }

    private static final class NamedServlet extends HttpServlet {
    }
}
