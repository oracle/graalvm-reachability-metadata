/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.Registration;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.web.exchanges.HttpExchange;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.Include;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.boot.servlet.actuate.web.exchanges.HttpExchangesFilter;
import org.springframework.boot.servlet.actuate.web.mappings.FilterRegistrationMappingDescription;
import org.springframework.boot.servlet.actuate.web.mappings.FiltersMappingDescriptionProvider;
import org.springframework.boot.servlet.actuate.web.mappings.ServletRegistrationMappingDescription;
import org.springframework.boot.servlet.actuate.web.mappings.ServletsMappingDescriptionProvider;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.servlet.autoconfigure.ServletEncodingProperties;
import org.springframework.boot.servlet.autoconfigure.ServletEncodingProperties.HttpMessageType;
import org.springframework.boot.servlet.filter.ApplicationContextHeaderFilter;
import org.springframework.boot.servlet.filter.OrderedCharacterEncodingFilter;
import org.springframework.boot.servlet.filter.OrderedFilter;
import org.springframework.boot.servlet.filter.OrderedFormContentFilter;
import org.springframework.boot.servlet.filter.OrderedHiddenHttpMethodFilter;
import org.springframework.boot.servlet.filter.OrderedRequestContextFilter;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.util.unit.DataSize;
import org.springframework.web.context.support.StaticWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_servletTest {

    @Test
    void multipartConfigFactoryConvertsDataSizesAndFallsBackForNegativeLimits() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setLocation("/tmp/uploads");
        factory.setMaxFileSize(DataSize.ofKilobytes(512));
        factory.setMaxRequestSize(DataSize.ofMegabytes(2));
        factory.setFileSizeThreshold(DataSize.ofKilobytes(8));

        MultipartConfigElement config = factory.createMultipartConfig();

        assertThat(config.getLocation()).isEqualTo("/tmp/uploads");
        assertThat(config.getMaxFileSize()).isEqualTo(512 * 1024);
        assertThat(config.getMaxRequestSize()).isEqualTo(2 * 1024 * 1024);
        assertThat(config.getFileSizeThreshold()).isEqualTo(8 * 1024);

        MultipartConfigFactory defaults = new MultipartConfigFactory();
        defaults.setMaxFileSize(DataSize.ofBytes(-1));
        defaults.setMaxRequestSize(DataSize.ofBytes(-1));
        defaults.setFileSizeThreshold(DataSize.ofBytes(-1));

        MultipartConfigElement defaultConfig = defaults.createMultipartConfig();

        assertThat(defaultConfig.getLocation()).isEqualTo("");
        assertThat(defaultConfig.getMaxFileSize()).isEqualTo(-1);
        assertThat(defaultConfig.getMaxRequestSize()).isEqualTo(-1);
        assertThat(defaultConfig.getFileSizeThreshold()).isZero();
    }

    @Test
    void multipartPropertiesExposeDefaultsAndCreateStrictLazyMultipartConfig() {
        MultipartProperties properties = new MultipartProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(1));
        assertThat(properties.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(10));
        assertThat(properties.getFileSizeThreshold()).isEqualTo(DataSize.ofBytes(0));

        properties.setLocation("/var/spring/uploads");
        properties.setMaxFileSize(DataSize.ofMegabytes(3));
        properties.setMaxRequestSize(DataSize.ofMegabytes(7));
        properties.setFileSizeThreshold(DataSize.ofKilobytes(32));
        properties.setResolveLazily(true);
        properties.setStrictServletCompliance(true);

        MultipartConfigElement config = properties.createMultipartConfig();

        assertThat(properties.isResolveLazily()).isTrue();
        assertThat(properties.isStrictServletCompliance()).isTrue();
        assertThat(config.getLocation()).isEqualTo("/var/spring/uploads");
        assertThat(config.getMaxFileSize()).isEqualTo(3 * 1024 * 1024);
        assertThat(config.getMaxRequestSize()).isEqualTo(7 * 1024 * 1024);
        assertThat(config.getFileSizeThreshold()).isEqualTo(32 * 1024);
    }

    @Test
    void servletEncodingPropertiesApplyDefaultAndExplicitForceRules() {
        ServletEncodingProperties properties = new ServletEncodingProperties();

        assertThat(properties.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(properties.shouldForce(HttpMessageType.REQUEST)).isTrue();
        assertThat(properties.shouldForce(HttpMessageType.RESPONSE)).isFalse();

        properties.setCharset(StandardCharsets.ISO_8859_1);
        properties.setForce(true);

        assertThat(properties.getCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(properties.isForce()).isTrue();
        assertThat(properties.shouldForce(HttpMessageType.REQUEST)).isTrue();
        assertThat(properties.shouldForce(HttpMessageType.RESPONSE)).isTrue();

        properties.setForceRequest(false);
        properties.setForceResponse(true);

        assertThat(properties.isForceRequest()).isFalse();
        assertThat(properties.isForceResponse()).isTrue();
        assertThat(properties.shouldForce(HttpMessageType.REQUEST)).isFalse();
        assertThat(properties.shouldForce(HttpMessageType.RESPONSE)).isTrue();
    }

    @Test
    void orderedServletFiltersExposeDefaultAndConfiguredOrder() {
        OrderedCharacterEncodingFilter characterEncodingFilter = new OrderedCharacterEncodingFilter();
        OrderedFormContentFilter formContentFilter = new OrderedFormContentFilter();
        OrderedHiddenHttpMethodFilter hiddenHttpMethodFilter = new OrderedHiddenHttpMethodFilter();
        OrderedRequestContextFilter requestContextFilter = new OrderedRequestContextFilter();

        assertThat(characterEncodingFilter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(formContentFilter.getOrder()).isEqualTo(OrderedFormContentFilter.DEFAULT_ORDER);
        assertThat(hiddenHttpMethodFilter.getOrder()).isEqualTo(OrderedHiddenHttpMethodFilter.DEFAULT_ORDER);
        assertThat(requestContextFilter.getOrder()).isEqualTo(OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 105);

        characterEncodingFilter.setOrder(11);
        formContentFilter.setOrder(12);
        hiddenHttpMethodFilter.setOrder(13);
        requestContextFilter.setOrder(14);

        assertThat(characterEncodingFilter.getOrder()).isEqualTo(11);
        assertThat(formContentFilter.getOrder()).isEqualTo(12);
        assertThat(hiddenHttpMethodFilter.getOrder()).isEqualTo(13);
        assertThat(requestContextFilter.getOrder()).isEqualTo(14);
    }

    @Test
    void orderedHiddenHttpMethodFilterOverridesPostMethodBeforeContinuingChain() throws ServletException, IOException {
        OrderedHiddenHttpMethodFilter filter = new OrderedHiddenHttpMethodFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders/42");
        request.setContentType("application/x-www-form-urlencoded");
        request.addParameter("_method", "PATCH");
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> observedMethods = new ArrayList<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> observedMethods
                .add(((HttpServletRequest) servletRequest).getMethod()));

        assertThat(observedMethods).containsExactly("PATCH");
        assertThat(request.getMethod()).isEqualTo("POST");
    }

    @Test
    void applicationContextHeaderFilterAddsContextIdBeforeContinuingChain() throws ServletException, IOException {
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.setId("test-application");
        ApplicationContextHeaderFilter filter = new ApplicationContextHeaderFilter(applicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> chainEvents = new ArrayList<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainEvents.add("continued"));

        assertThat(response.getHeader(ApplicationContextHeaderFilter.HEADER_NAME)).isEqualTo("test-application");
        assertThat(chainEvents).containsExactly("continued");
    }

    @Test
    void httpExchangesFilterRecordsServletExchangeDetails() throws ServletException, IOException {
        RecordingRepository repository = new RecordingRepository();
        HttpExchangesFilter filter = new HttpExchangesFilter(repository, EnumSet.allOf(Include.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.setServerName("example.com");
        request.setQueryString("id=42");
        request.setRemoteAddr("192.0.2.15");
        request.addHeader("X-Request-Id", "request-123");
        request.setUserPrincipal(() -> "alice");
        request.setSession(new MockHttpSession(null, "session-123"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.addHeader("X-Response-Id", "response-456");
            httpResponse.setStatus(HttpServletResponse.SC_CREATED);
        });

        assertThat(repository.findAll()).singleElement().satisfies((exchange) -> {
            assertThat(exchange.getRequest().getMethod()).isEqualTo("POST");
            assertThat(exchange.getRequest().getUri().getPath()).isEqualTo("/orders");
            assertThat(exchange.getRequest().getUri().getQuery()).isEqualTo("id=42");
            assertThat(exchange.getRequest().getRemoteAddress()).isEqualTo("192.0.2.15");
            assertThat(exchange.getRequest().getHeaders()).containsEntry("X-Request-Id", List.of("request-123"));
            assertThat(exchange.getResponse().getStatus()).isEqualTo(HttpServletResponse.SC_CREATED);
            assertThat(exchange.getResponse().getHeaders()).containsEntry("X-Response-Id", List.of("response-456"));
            assertThat(exchange.getPrincipal().getName()).isEqualTo("alice");
            assertThat(exchange.getSession().getId()).isEqualTo("session-123");
            assertThat(exchange.getTimeTaken()).isNotNull();
        });

        filter.setOrder(99);
        assertThat(filter.getOrder()).isEqualTo(99);
    }

    @Test
    void httpExchangesFilterDoesNotRecordRequestsWithInvalidRequestUrl() throws ServletException, IOException {
        RecordingRepository repository = new RecordingRepository();
        HttpExchangesFilter filter = new HttpExchangesFilter(repository, Include.defaultIncludes());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/invalid");
        request.setServerName("invalid host name");
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> chainEvents = new ArrayList<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainEvents.add("continued"));

        assertThat(chainEvents).containsExactly("continued");
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void mappingDescriptionProvidersDescribeServletContextRegistrations() {
        TestServletContext servletContext = new TestServletContext();
        servletContext.addFilterRegistration(new TestFilterRegistration("encoding",
                OrderedCharacterEncodingFilter.class.getName(), List.of("dispatcherServlet"), List.of("/*")));
        servletContext.addServletRegistration(new TestServletRegistration("dispatcherServlet",
                TestServlet.class.getName(), List.of("/app/*", "/api/*")));

        try (StaticWebApplicationContext context = new StaticWebApplicationContext()) {
            context.setServletContext(servletContext);
            context.refresh();

            List<FilterRegistrationMappingDescription> filters = new FiltersMappingDescriptionProvider()
                    .describeMappings(context);
            List<ServletRegistrationMappingDescription> servlets = new ServletsMappingDescriptionProvider()
                    .describeMappings(context);

            assertThat(new FiltersMappingDescriptionProvider().getMappingName()).isEqualTo("servletFilters");
            assertThat(filters).singleElement().satisfies((description) -> {
                assertThat(description.getName()).isEqualTo("encoding");
                assertThat(description.getClassName()).isEqualTo(OrderedCharacterEncodingFilter.class.getName());
                assertThat(description.getUrlPatternMappings()).containsExactlyInAnyOrder("/*");
                assertThat(description.getServletNameMappings()).containsExactlyInAnyOrder("dispatcherServlet");
            });
            assertThat(new ServletsMappingDescriptionProvider().getMappingName()).isEqualTo("servlets");
            assertThat(servlets).singleElement().satisfies((description) -> {
                assertThat(description.getName()).isEqualTo("dispatcherServlet");
                assertThat(description.getClassName()).isEqualTo(TestServlet.class.getName());
                assertThat(description.getMappings()).containsExactlyInAnyOrder("/app/*", "/api/*");
            });
        }
    }

    @Test
    void mappingDescriptionProvidersReturnEmptyMappingsForNonWebContexts() {
        try (StaticApplicationContext context = new StaticApplicationContext()) {
            context.refresh();

            assertThat(new FiltersMappingDescriptionProvider().describeMappings(context)).isEmpty();
            assertThat(new ServletsMappingDescriptionProvider().describeMappings(context)).isEmpty();
        }
    }

    private static final class RecordingRepository implements HttpExchangeRepository {

        private final List<HttpExchange> exchanges = new ArrayList<>();

        @Override
        public List<HttpExchange> findAll() {
            return List.copyOf(this.exchanges);
        }

        @Override
        public void add(HttpExchange exchange) {
            this.exchanges.add(exchange);
        }

    }

    private static final class TestServletContext extends MockServletContext {

        private final Map<String, FilterRegistration> filterRegistrations = new LinkedHashMap<>();

        private final Map<String, ServletRegistration> servletRegistrations = new LinkedHashMap<>();

        @Override
        public void addFilterRegistration(FilterRegistration registration) {
            this.filterRegistrations.put(registration.getName(), registration);
        }

        void addServletRegistration(ServletRegistration registration) {
            this.servletRegistrations.put(registration.getName(), registration);
        }

        @Override
        public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
            return Collections.unmodifiableMap(this.filterRegistrations);
        }

        @Override
        public Map<String, ? extends ServletRegistration> getServletRegistrations() {
            return Collections.unmodifiableMap(this.servletRegistrations);
        }

    }

    private abstract static class TestRegistration implements Registration {

        private final String name;

        private final String className;

        private final Map<String, String> initParameters = new LinkedHashMap<>();

        TestRegistration(String name, String className) {
            this.name = name;
            this.className = className;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String getClassName() {
            return this.className;
        }

        @Override
        public boolean setInitParameter(String name, String value) {
            if (this.initParameters.containsKey(name)) {
                return false;
            }
            this.initParameters.put(name, value);
            return true;
        }

        @Override
        public String getInitParameter(String name) {
            return this.initParameters.get(name);
        }

        @Override
        public Set<String> setInitParameters(Map<String, String> initParameters) {
            Set<String> conflicts = initParameters.keySet()
                    .stream()
                    .filter(this.initParameters::containsKey)
                    .collect(Collectors.toSet());
            if (conflicts.isEmpty()) {
                this.initParameters.putAll(initParameters);
            }
            return conflicts;
        }

        @Override
        public Map<String, String> getInitParameters() {
            return Collections.unmodifiableMap(this.initParameters);
        }

    }

    private static final class TestFilterRegistration extends TestRegistration implements FilterRegistration {

        private final List<String> servletNameMappings;

        private final List<String> urlPatternMappings;

        TestFilterRegistration(String name, String className, List<String> servletNameMappings,
                List<String> urlPatternMappings) {
            super(name, className);
            this.servletNameMappings = servletNameMappings;
            this.urlPatternMappings = urlPatternMappings;
        }

        @Override
        public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter,
                String... servletNames) {
            throw new UnsupportedOperationException("Mappings are fixed for this test registration");
        }

        @Override
        public Collection<String> getServletNameMappings() {
            return this.servletNameMappings;
        }

        @Override
        public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter,
                String... urlPatterns) {
            throw new UnsupportedOperationException("Mappings are fixed for this test registration");
        }

        @Override
        public Collection<String> getUrlPatternMappings() {
            return this.urlPatternMappings;
        }

    }

    private static final class TestServletRegistration extends TestRegistration implements ServletRegistration {

        private final List<String> mappings;

        TestServletRegistration(String name, String className, List<String> mappings) {
            super(name, className);
            this.mappings = mappings;
        }

        @Override
        public Set<String> addMapping(String... urlPatterns) {
            throw new UnsupportedOperationException("Mappings are fixed for this test registration");
        }

        @Override
        public Collection<String> getMappings() {
            return this.mappings;
        }

        @Override
        public String getRunAsRole() {
            return null;
        }

    }

    private static final class TestServlet extends HttpServlet {
    }

}
