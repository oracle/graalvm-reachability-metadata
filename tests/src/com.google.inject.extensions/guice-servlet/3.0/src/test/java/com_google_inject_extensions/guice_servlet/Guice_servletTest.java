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
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
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

    private static final class RequestScopedValue {
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
