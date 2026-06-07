/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
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
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeAttribute;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletPath;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.JspTemplateAvailabilityProvider;
import org.springframework.boot.webmvc.autoconfigure.WebMvcProperties;
import org.springframework.boot.webmvc.autoconfigure.WebMvcProperties.MatchingStrategy;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.autoconfigure.error.DefaultErrorViewResolver;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.DefaultMessageCodesResolver;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_webmvcTest {

    @Test
    void springApplicationDeducesServletWebApplicationType() {
        SpringApplication application = new SpringApplication(BasicConfiguration.class);

        assertThat(application.getWebApplicationType()).isEqualTo(WebApplicationType.SERVLET);
    }

    @Test
    void webMvcPropertiesExposeNestedConfiguration() {
        WebMvcProperties properties = new WebMvcProperties();

        properties.setMessageCodesResolverFormat(DefaultMessageCodesResolver.Format.POSTFIX_ERROR_CODE);
        properties.setPublishRequestHandledEvents(false);
        properties.setLogRequestDetails(true);
        properties.setLogResolvedException(false);
        properties.setDispatchOptionsRequest(true);
        properties.setDispatchTraceRequest(true);
        properties.setStaticPathPattern("/assets/**");
        properties.setWebjarsPathPattern("/vendor/**");
        properties.getFormat().setDate("yyyy-MM-dd");
        properties.getFormat().setTime("HH:mm:ss");
        properties.getFormat().setDateTime("yyyy-MM-dd'T'HH:mm:ss");
        properties.getAsync().setRequestTimeout(Duration.ofSeconds(3));
        properties.getServlet().setPath("/api");
        properties.getServlet().setLoadOnStartup(2);
        properties.getView().setPrefix("/WEB-INF/views/");
        properties.getView().setSuffix(".jsp");
        properties.getContentnegotiation().setFavorParameter(true);
        properties.getContentnegotiation().setParameterName("format");
        properties.getContentnegotiation().setMediaTypes(Map.of("json", MediaType.APPLICATION_JSON));
        properties.getContentnegotiation().setDefaultContentTypes(List.of(MediaType.APPLICATION_JSON));
        properties.getPathmatch().setMatchingStrategy(MatchingStrategy.PATH_PATTERN_PARSER);
        properties.getProblemdetails().setEnabled(true);
        properties.getApiversion().setRequired(true);
        properties.getApiversion().setDefaultVersion("2026-06-07");
        properties.getApiversion().setSupported(List.of("2026-06-07", "2026-07-01"));
        properties.getApiversion().setDetectSupported(false);
        properties.getApiversion().getUse().setHeader("X-API-Version");
        properties.getApiversion().getUse().setQueryParameter("api-version");
        properties.getApiversion().getUse().setPathSegment(1);
        properties.getApiversion().getUse()
                .setMediaTypeParameter(Map.of(MediaType.APPLICATION_JSON, "version"));

        assertThat(properties.getMessageCodesResolverFormat())
                .isEqualTo(DefaultMessageCodesResolver.Format.POSTFIX_ERROR_CODE);
        assertThat(properties.isPublishRequestHandledEvents()).isFalse();
        assertThat(properties.isLogRequestDetails()).isTrue();
        assertThat(properties.isLogResolvedException()).isFalse();
        assertThat(properties.isDispatchOptionsRequest()).isTrue();
        assertThat(properties.isDispatchTraceRequest()).isTrue();
        assertThat(properties.getStaticPathPattern()).isEqualTo("/assets/**");
        assertThat(properties.getWebjarsPathPattern()).isEqualTo("/vendor/**");
        assertThat(properties.getFormat().getDate()).isEqualTo("yyyy-MM-dd");
        assertThat(properties.getFormat().getTime()).isEqualTo("HH:mm:ss");
        assertThat(properties.getFormat().getDateTime()).isEqualTo("yyyy-MM-dd'T'HH:mm:ss");
        assertThat(properties.getAsync().getRequestTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getServlet().getPath()).isEqualTo("/api");
        assertThat(properties.getServlet().getServletMapping()).isEqualTo("/api/*");
        assertThat(properties.getServlet().getPath("/orders")).isEqualTo("/api/orders");
        assertThat(properties.getServlet().getServletPrefix()).isEqualTo("/api");
        assertThat(properties.getServlet().getLoadOnStartup()).isEqualTo(2);
        assertThat(properties.getView().getPrefix()).isEqualTo("/WEB-INF/views/");
        assertThat(properties.getView().getSuffix()).isEqualTo(".jsp");
        assertThat(properties.getContentnegotiation().isFavorParameter()).isTrue();
        assertThat(properties.getContentnegotiation().getParameterName()).isEqualTo("format");
        assertThat(properties.getContentnegotiation().getMediaTypes())
                .containsEntry("json", MediaType.APPLICATION_JSON);
        assertThat(properties.getContentnegotiation().getDefaultContentTypes())
                .containsExactly(MediaType.APPLICATION_JSON);
        assertThat(properties.getPathmatch().getMatchingStrategy()).isEqualTo(MatchingStrategy.PATH_PATTERN_PARSER);
        assertThat(properties.getProblemdetails().isEnabled()).isTrue();
        assertThat(properties.getApiversion().getRequired()).isTrue();
        assertThat(properties.getApiversion().getDefaultVersion()).isEqualTo("2026-06-07");
        assertThat(properties.getApiversion().getSupported()).containsExactly("2026-06-07", "2026-07-01");
        assertThat(properties.getApiversion().getDetectSupported()).isFalse();
        assertThat(properties.getApiversion().getUse().getHeader()).isEqualTo("X-API-Version");
        assertThat(properties.getApiversion().getUse().getQueryParameter()).isEqualTo("api-version");
        assertThat(properties.getApiversion().getUse().getPathSegment()).isEqualTo(1);
        assertThat(properties.getApiversion().getUse().getMediaTypeParameter())
                .containsEntry(MediaType.APPLICATION_JSON, "version");
    }

    @Test
    void dispatcherServletPathDerivesRelativePathsPrefixesAndMappings() {
        DispatcherServletPath rootPath = () -> "/";
        DispatcherServletPath nestedPath = () -> "/admin";

        assertThat(rootPath.getRelativePath("/health")).isEqualTo("/health");
        assertThat(rootPath.getPrefix()).isEmpty();
        assertThat(rootPath.getServletUrlMapping()).isEqualTo("/");
        assertThat(nestedPath.getRelativePath("/users")).isEqualTo("/admin/users");
        assertThat(nestedPath.getPrefix()).isEqualTo("/admin");
        assertThat(nestedPath.getServletUrlMapping()).isEqualTo("/admin/*");
    }

    @Test
    void dispatcherServletRegistrationBeanUsesDispatcherServletPathAndMappings() {
        DispatcherServlet dispatcherServlet = new DispatcherServlet();
        DispatcherServletRegistrationBean registration = new DispatcherServletRegistrationBean(dispatcherServlet,
                "/api");

        registration.setName("dispatcherServlet");
        registration.setLoadOnStartup(1);

        assertThat(registration.getServlet()).isSameAs(dispatcherServlet);
        assertThat(registration.getPath()).isEqualTo("/api");
        assertThat(registration.getServletUrlMapping()).isEqualTo("/api/*");
        assertThat(registration.getUrlMappings()).contains("/api/*");
        assertThat(registration.getServletName()).isEqualTo("dispatcherServlet");
    }

    @Test
    void defaultWebMvcRegistrationsDoNotOverrideMvcInfrastructure() {
        WebMvcRegistrations registrations = new WebMvcRegistrations() {
        };

        assertThat(registrations.getRequestMappingHandlerMapping()).isNull();
        assertThat(registrations.getRequestMappingHandlerAdapter()).isNull();
        assertThat(registrations.getExceptionHandlerExceptionResolver()).isNull();
    }

    @Test
    void defaultErrorAttributesReturnIncludedDetailsForStoredException() {
        DefaultErrorAttributes attributes = new DefaultErrorAttributes();
        SimpleWebRequest request = new SimpleWebRequest("/orders/99");
        IllegalStateException failure = new IllegalStateException("missing order");
        request.setAttribute("jakarta.servlet.error.status_code", HttpStatus.NOT_FOUND.value(),
                RequestAttributes.SCOPE_REQUEST);
        request.setAttribute("jakarta.servlet.error.request_uri", "/orders/99", RequestAttributes.SCOPE_REQUEST);
        request.setAttribute("jakarta.servlet.error.exception", failure, RequestAttributes.SCOPE_REQUEST);

        Map<String, Object> result = attributes.getErrorAttributes(request,
                ErrorAttributeOptions.of(Include.STATUS, Include.ERROR, Include.PATH, Include.MESSAGE,
                        Include.EXCEPTION, Include.STACK_TRACE));

        assertThat(attributes.getOrder()).isEqualTo(Integer.MIN_VALUE);
        assertThat(attributes.getError(request)).isSameAs(failure);
        assertThat(result).containsEntry("status", 404)
                .containsEntry("error", "Not Found")
                .containsEntry("path", "/orders/99")
                .containsEntry("message", "missing order")
                .containsEntry("exception", IllegalStateException.class.getName());
        assertThat(result.get("trace")).asString().contains(IllegalStateException.class.getName());
    }

    @Test
    void defaultErrorAttributesIncludeBindingErrorsWhenRequested() {
        DefaultErrorAttributes attributes = new DefaultErrorAttributes();
        SimpleWebRequest request = new SimpleWebRequest("/registrations");
        BindException failure = new BindException(new RegistrationForm(), "registrationForm");
        failure.reject("registration.invalid", "Registration is invalid");
        request.setAttribute("jakarta.servlet.error.status_code", HttpStatus.BAD_REQUEST.value(),
                RequestAttributes.SCOPE_REQUEST);
        request.setAttribute("jakarta.servlet.error.request_uri", "/registrations", RequestAttributes.SCOPE_REQUEST);
        request.setAttribute("jakarta.servlet.error.exception", failure, RequestAttributes.SCOPE_REQUEST);

        Map<String, Object> result = attributes.getErrorAttributes(request,
                ErrorAttributeOptions.of(Include.STATUS, Include.ERROR, Include.PATH, Include.MESSAGE,
                        Include.EXCEPTION, Include.BINDING_ERRORS));

        assertThat(result).containsEntry("status", 400)
                .containsEntry("error", "Bad Request")
                .containsEntry("path", "/registrations")
                .containsEntry("exception", BindException.class.getName());
        assertThat(result.get("message")).asString()
                .contains("Validation failed for object='registrationForm'")
                .contains("Error count: 1");
        assertThat(result.get("errors")).asList()
                .singleElement()
                .satisfies((error) -> assertThat(((MessageSourceResolvable) error).getDefaultMessage())
                        .isEqualTo("Registration is invalid"));
    }

    @Test
    void jspTemplateAvailabilityProviderRequiresJasperAndJspResource() {
        JspTemplateAvailabilityProvider provider = new JspTemplateAvailabilityProvider();
        Environment environment = new StandardEnvironment();
        ClassLoader classLoader = getClass().getClassLoader();

        boolean available = provider.isTemplateAvailable("home", environment, classLoader, new DefaultResourceLoader());

        assertThat(available).isFalse();
    }

    @Test
    void basicErrorControllerBuildsErrorResponseUsingErrorPropertiesAndRequestParameters() {
        ErrorProperties errorProperties = new ErrorProperties();
        errorProperties.setIncludeException(true);
        errorProperties.setIncludeMessage(IncludeAttribute.ON_PARAM);
        errorProperties.setIncludeStacktrace(IncludeAttribute.ON_PARAM);
        errorProperties.setIncludePath(IncludeAttribute.ON_PARAM);
        BasicErrorController controller = new BasicErrorController(new CapturingErrorAttributes(), errorProperties);
        SimpleHttpServletRequest request = new SimpleHttpServletRequest("/orders/42");
        request.setAttribute("jakarta.servlet.error.status_code", HttpStatus.CONFLICT.value());
        request.setParameter("message", "true");
        request.setParameter("trace", "true");
        request.setParameter("path", "true");

        ResponseEntity<Map<String, Object>> response = controller.error(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("exceptionIncluded", true)
                .containsEntry("messageIncluded", true)
                .containsEntry("stackTraceIncluded", true)
                .containsEntry("pathIncluded", true)
                .containsEntry("bindingErrorsIncluded", false);
    }

    @Test
    void defaultErrorViewResolverUsesStaticErrorPageForStatusCode(@TempDir Path directory) throws IOException {
        Path staticLocation = Files.createDirectories(directory.resolve("static"));
        Path errorDirectory = Files.createDirectories(staticLocation.resolve("error"));
        Files.writeString(errorDirectory.resolve("404.html"), "<html><body>Not found</body></html>");
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        try {
            WebProperties.Resources resources = new WebProperties.Resources();
            String staticLocationUri = staticLocation.toUri().toString();
            resources.setStaticLocations(new String[] { staticLocationUri.endsWith("/") ? staticLocationUri
                    : staticLocationUri + "/" });
            DefaultErrorViewResolver resolver = new DefaultErrorViewResolver(applicationContext, resources);
            Map<String, Object> model = Map.of("status", HttpStatus.NOT_FOUND.value(), "path", "/missing");

            ModelAndView modelAndView = resolver.resolveErrorView(new SimpleHttpServletRequest("/missing"),
                    HttpStatus.NOT_FOUND, model);

            assertThat(modelAndView).isNotNull();
            assertThat(modelAndView.getView()).isNotNull();
            assertThat(modelAndView.getViewName()).isNull();
            assertThat(modelAndView.getModel()).containsEntry("status", HttpStatus.NOT_FOUND.value())
                    .containsEntry("path", "/missing");
        }
        finally {
            applicationContext.close();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BasicConfiguration {

    }

    static final class RegistrationForm {

    }

    private static final class CapturingErrorAttributes implements ErrorAttributes {

        @Override
        public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
            Map<String, Object> result = new HashMap<>();
            result.put("exceptionIncluded", options.isIncluded(Include.EXCEPTION));
            result.put("messageIncluded", options.isIncluded(Include.MESSAGE));
            result.put("stackTraceIncluded", options.isIncluded(Include.STACK_TRACE));
            result.put("pathIncluded", options.isIncluded(Include.PATH));
            result.put("bindingErrorsIncluded", options.isIncluded(Include.BINDING_ERRORS));
            return result;
        }

        @Override
        public Throwable getError(WebRequest webRequest) {
            return null;
        }

    }

    private static final class SimpleHttpServletRequest implements HttpServletRequest {

        private final Map<String, Object> attributes = new HashMap<>();

        private final Map<String, String[]> parameters = new HashMap<>();

        private final String requestUri;

        SimpleHttpServletRequest(String requestUri) {
            this.requestUri = requestUri;
        }

        void setParameter(String name, String value) {
            this.parameters.put(name, new String[] { value });
        }

        @Override
        public Object getAttribute(String name) {
            return this.attributes.get(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(this.attributes.keySet());
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException {
            // The test request does not decode a body.
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
        public String getContentType() {
            return null;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return null;
        }

        @Override
        public String getParameter(String name) {
            String[] values = this.parameters.get(name);
            return (values != null && values.length > 0) ? values[0] : null;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(this.parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return this.parameters.get(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return this.parameters;
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
        public BufferedReader getReader() throws IOException {
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
        public void setAttribute(String name, Object value) {
            this.attributes.put(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            this.attributes.remove(name);
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
        public RequestDispatcher getRequestDispatcher(String path) {
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

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public AsyncContext startAsync() throws IllegalStateException {
            return null;
        }

        @Override
        public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
                throws IllegalStateException {
            return null;
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
        public long getDateHeader(String name) {
            return -1;
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
            return null;
        }

        @Override
        public String getRequestURI() {
            return this.requestUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            return new java.lang.StringBuffer("http://localhost").append(this.requestUri);
        }

        @Override
        public String getServletPath() {
            return this.requestUri;
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
        public String changeSessionId() {
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
        public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
            return false;
        }

        @Override
        public void login(String username, String password) throws ServletException {
            // Authentication is outside the scope of the error-controller test.
        }

        @Override
        public void logout() throws ServletException {
            // Authentication is outside the scope of the error-controller test.
        }

        @Override
        public Collection<Part> getParts() throws IOException, ServletException {
            return Collections.emptyList();
        }

        @Override
        public Part getPart(String name) throws IOException, ServletException {
            return null;
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
            return null;
        }

    }

    private static final class SimpleWebRequest implements WebRequest {

        private final Map<String, Object> requestAttributes = new HashMap<>();

        private final String path;

        SimpleWebRequest(String path) {
            this.path = path;
        }

        @Override
        public Object getAttribute(String name, int scope) {
            return getAttributes(scope).get(name);
        }

        @Override
        public void setAttribute(String name, Object value, int scope) {
            getAttributes(scope).put(name, value);
        }

        @Override
        public void removeAttribute(String name, int scope) {
            getAttributes(scope).remove(name);
        }

        @Override
        public String[] getAttributeNames(int scope) {
            return getAttributes(scope).keySet().toArray(String[]::new);
        }

        @Override
        public void registerDestructionCallback(String name, Runnable callback, int scope) {
            // No backing servlet container is needed for these request-attribute tests.
        }

        @Override
        public Object resolveReference(String key) {
            return null;
        }

        @Override
        public String getSessionId() {
            return "test-session";
        }

        @Override
        public Object getSessionMutex() {
            return this;
        }

        @Override
        public String getHeader(String headerName) {
            return null;
        }

        @Override
        public String[] getHeaderValues(String headerName) {
            return null;
        }

        @Override
        public Iterator<String> getHeaderNames() {
            return Collections.emptyIterator();
        }

        @Override
        public String getParameter(String parameterName) {
            return null;
        }

        @Override
        public String[] getParameterValues(String parameterName) {
            return null;
        }

        @Override
        public Iterator<String> getParameterNames() {
            return Collections.emptyIterator();
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.emptyMap();
        }

        @Override
        public Locale getLocale() {
            return Locale.ENGLISH;
        }

        @Override
        public String getContextPath() {
            return "";
        }

        @Override
        public String getRemoteUser() {
            return null;
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public boolean isUserInRole(String role) {
            return false;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public boolean checkNotModified(long lastModifiedTimestamp) {
            return false;
        }

        @Override
        public boolean checkNotModified(String etag) {
            return false;
        }

        @Override
        public boolean checkNotModified(String etag, long lastModifiedTimestamp) {
            return false;
        }

        @Override
        public String getDescription(boolean includeClientInfo) {
            return "uri=" + this.path;
        }

        private Map<String, Object> getAttributes(int scope) {
            assertThat(scope).isEqualTo(RequestAttributes.SCOPE_REQUEST);
            return this.requestAttributes;
        }

    }

}
