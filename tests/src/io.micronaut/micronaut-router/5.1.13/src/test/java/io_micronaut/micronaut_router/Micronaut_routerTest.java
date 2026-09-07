/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_router;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.env.Environment;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestAttribute;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.web.router.DefaultRouteBuilder;
import io.micronaut.web.router.ErrorRoute;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.RouteMatchUtils;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.StatusRoute;
import io.micronaut.web.router.UriRoute;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import io.micronaut.web.router.resource.StaticResourceResolver;
import io.micronaut.web.router.uri.UriUtil;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Controller("/catalog")
public class Micronaut_routerTest {
    @Test
    @Timeout(55)
    void discoversExecutesAndSelectsAnnotatedRoutes() {
        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            Router router = context.getBean(Router.class);

            UriRouteMatch<?, ?> itemRoute = findClosest(
                    router,
                    HttpRequest.GET("/catalog/items/42").accept(MediaType.TEXT_PLAIN_TYPE));
            assertThat(itemRoute.getHttpMethod() == HttpMethod.GET).isTrue();
            assertThat(itemRoute.getVariableValues()).containsEntry("id", "42");
            assertThat(itemRoute.getRouteInfo().getProduces()).containsExactly(MediaType.TEXT_PLAIN_TYPE);
            assertThat(itemRoute.invoke()).isEqualTo("item-42");

            UriRouteMatch<?, ?> featuredRoute = findClosest(
                    router,
                    HttpRequest.GET("/catalog/items/featured").accept(MediaType.TEXT_PLAIN_TYPE));
            assertThat(featuredRoute.getMethodName()).isEqualTo("featuredItem");
            assertThat(featuredRoute.invoke()).isEqualTo("featured-item");

            HttpRequest<String> createRequest = HttpRequest.POST("/catalog/items", "pencil")
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
                    .accept(MediaType.TEXT_PLAIN_TYPE);
            UriRouteMatch<?, ?> createRoute = findClosest(router, createRequest);
            assertThat(createRoute.getRouteInfo().getConsumes()).containsExactly(MediaType.TEXT_PLAIN_TYPE);
            assertThat(createRoute.getRouteInfo().needsRequestBody()).isTrue();
            assertThat(createRoute.invoke("pencil")).isEqualTo("created-pencil");

            List<UriRouteMatch<Object, Object>> routesForPath =
                    router.findAny(HttpRequest.GET("/catalog/items/42"));
            boolean hasGetRoute = false;
            boolean hasHeadRoute = false;
            for (UriRouteMatch<?, ?> route : routesForPath) {
                hasGetRoute |= route.getHttpMethod() == HttpMethod.GET;
                hasHeadRoute |= route.getHttpMethod() == HttpMethod.HEAD;
            }
            assertThat(routesForPath).hasSize(2);
            assertThat(hasGetRoute).isTrue();
            assertThat(hasHeadRoute).isTrue();
            assertThat(router.POST("/catalog/items/42")).isEmpty();
        }
    }

    @Test
    @Timeout(55)
    void invokesAnAnnotatedRouteWithRequestValues() {
        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            Router router = context.getBean(Router.class);
            HttpRequest<?> request = HttpRequest.GET("/catalog/search?term=router")
                    .header("X-Trace", "trace-12")
                    .accept(MediaType.TEXT_PLAIN_TYPE);

            UriRouteMatch<?, ?> routeMatch = findClosest(router, request);

            assertThat(routeMatch.invoke("router", "trace-12", "north"))
                    .isEqualTo("router|trace-12|north");
        }
    }

    @Test
    @Timeout(55)
    void filtersVersionedRoutesUsingHeaderParameterAndDefaultVersion() {
        Map<String, Object> properties = Map.ofEntries(
                Map.entry("micronaut.router.versioning.enabled", true),
                Map.entry("micronaut.router.versioning.header.enabled", true),
                Map.entry("micronaut.router.versioning.header.names", List.of("X-Release")),
                Map.entry("micronaut.router.versioning.parameter.enabled", true),
                Map.entry("micronaut.router.versioning.parameter.names", List.of("api-revision")),
                Map.entry("micronaut.router.versioning.default-version", "v1"));

        try (ApplicationContext context = ApplicationContext.run(properties, Environment.TEST)) {
            Router router = context.getBean(Router.class);

            HttpRequest<?> headerRequest = HttpRequest.GET("/catalog/versioned")
                    .header("X-Release", "v2")
                    .accept(MediaType.TEXT_PLAIN_TYPE);
            assertThat(findClosest(router, headerRequest).invoke()).isEqualTo("version-two");

            MutableHttpRequest<?> parameterRequest = HttpRequest.GET("/catalog/versioned")
                    .accept(MediaType.TEXT_PLAIN_TYPE);
            parameterRequest.getParameters().add("api-revision", "v2");
            assertThat(findClosest(router, parameterRequest).invoke()).isEqualTo("version-two");

            HttpRequest<?> defaultVersionRequest = HttpRequest.GET("/catalog/versioned")
                    .accept(MediaType.TEXT_PLAIN_TYPE);
            assertThat(findClosest(router, defaultVersionRequest).invoke()).isEqualTo("version-one");
        }
    }

    @Test
    @Timeout(55)
    void bindsRouterConfigurationAndResolvesPackagedStaticResources() {
        Map<String, Object> properties = Map.ofEntries(
                Map.entry("micronaut.server.context-path", "/gateway/"),
                Map.entry("micronaut.router.static-resources.assets.enabled", true),
                Map.entry("micronaut.router.static-resources.assets.mapping", "/assets/**"),
                Map.entry(
                        "micronaut.router.static-resources.assets.paths",
                        List.of("classpath:META-INF/micronaut-configuration-schemas")));

        try (ApplicationContext context = ApplicationContext.run(properties, Environment.TEST)) {
            HyphenatedUriNamingStrategy namingStrategy = context.getBean(HyphenatedUriNamingStrategy.class);
            assertThat(namingStrategy.resolveUri(OrderHistoryController.class))
                    .isEqualTo("/gateway/order-history");
            assertThat(namingStrategy.resolveUri("OrderHistory")).isEqualTo("/gateway/order-history");

            StaticResourceResolver resolver = context.getBean(StaticResourceResolver.class);
            String configurationSchema = "/assets/io.micronaut.web.router.resource.StaticResourceConfiguration.json";
            URL resolvedResource = resolver.resolve(configurationSchema).orElseThrow();
            assertThat(resolvedResource.getPath()).endsWith("StaticResourceConfiguration.json");
            assertThat(resolver.resolve("/assets/missing.json")).isEmpty();
        }
    }

    @Test
    @Timeout(55)
    void resolvesStatusAndErrorRoutesAndStoresRouteAttributes() {
        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            Router router = context.getBean(Router.class);

            RouteMatch<?> statusRoute = router.route(Micronaut_routerTest.class, HttpStatus.NOT_FOUND).orElseThrow();
            assertThat(statusRoute.execute()).isEqualTo("catalog-not-found");

            IllegalArgumentException failure = new IllegalArgumentException("invalid item");
            RouteMatch<?> errorRoute = router.route(Micronaut_routerTest.class, failure).orElseThrow();
            assertThat(errorRoute).isInstanceOf(MethodBasedRouteMatch.class);
            MethodBasedRouteMatch<?, ?> methodBasedErrorRoute = (MethodBasedRouteMatch<?, ?>) errorRoute;
            assertThat(methodBasedErrorRoute.invoke()).isEqualTo("error: invalid item");

            MutableHttpRequest<?> request = HttpRequest.GET("/catalog/missing");
            RouteAttributes.setRouteMatch(request, statusRoute);
            RouteAttributes.setRouteInfo(request, statusRoute.getRouteInfo());
            assertThat(RouteAttributes.getRouteMatch(request)).contains(statusRoute);
            assertThat(RouteAttributes.getRouteInfo(request)).contains(statusRoute.getRouteInfo());
            assertThat(RouteMatchUtils.findRouteMatch(request)).contains(statusRoute);
        }
    }

    @Test
    @Timeout(55)
    void discoversServerFiltersByPathAndHttpMethod() {
        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            Router router = context.getBean(Router.class);

            assertThat(router.findFilters(HttpRequest.GET("/filtered/catalog"))).hasSize(1);
            assertThat(router.findFilters(HttpRequest.POST("/filtered/catalog", "item"))).isEmpty();
            assertThat(router.findFilters(HttpRequest.GET("/unfiltered/catalog"))).isEmpty();
        }
    }

    @Test
    @Timeout(55)
    void normalizesBrowserRequestTargetsForRfc3986Routing() {
        String browserPath = "/a path/\u00E9|x?bad=%zz&ok=%2F";

        assertThat(UriUtil.toValidPath(browserPath))
                .isEqualTo("/a%20path/%C3%A9%7Cx?bad=%25zz&ok=%2F");
        assertThat(UriUtil.toValidPath("//catalog/items")).isEqualTo("/catalog/items");
        assertThat(UriUtil.isValidPath("/catalog/items?limit=10")).isTrue();
        assertThat(UriUtil.isValidPath("/catalog/%invalid")).isFalse();
        assertThat(UriUtil.isValidPath("/catalog//items")).isFalse();
        assertThat(UriUtil.isRelative("/catalog/items")).isTrue();
        assertThat(UriUtil.isRelative("https://example.test/catalog")).isFalse();
    }

    @Test
    @Timeout(55)
    void buildsAndMatchesNestedProgrammaticRoutes() {
        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            ProgrammaticRouteBuilder routeBuilder = new ProgrammaticRouteBuilder(context);
            UriRoute catalogRoute = routeBuilder.GET(
                    "/programmatic/{category}",
                    Micronaut_routerTest.class,
                    "programmaticCatalog",
                    String.class);
            catalogRoute.nest(() -> routeBuilder.GET(
                    "/details/{item}",
                    Micronaut_routerTest.class,
                    "programmaticDetails",
                    String.class,
                    int.class));

            assertThat(routeBuilder.getUriRoutes()).hasSize(2);
            UriRouteMatch<Object, Object> catalogMatch = catalogRoute
                    .toRouteInfo()
                    .match("/programmatic/tools")
                    .orElseThrow();
            assertThat(catalogMatch.invoke()).isEqualTo("catalog-tools");

            UriRoute nestedRoute = routeBuilder.getUriRoutes().get(1);
            UriRouteMatch<Object, Object> routeMatch = nestedRoute
                    .toRouteInfo()
                    .match("/programmatic/tools/details/7")
                    .orElseThrow();

            assertThat(routeMatch.getVariableValues())
                    .containsEntry("category", "tools")
                    .containsEntry("item", "7");
            assertThat(routeMatch.invoke()).isEqualTo("tools-7");
        }
    }

    @Test
    @Timeout(55)
    void buildsAndExecutesConventionalResourceRoutes() {
        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            ProgrammaticRouteBuilder routeBuilder = new ProgrammaticRouteBuilder(context);
            String resourceBase = routeBuilder.getUriNamingStrategy().resolveUri(Micronaut_routerTest.class);
            routeBuilder.resources(Micronaut_routerTest.class);

            assertThat(routeBuilder.getUriRoutes()).hasSize(6);
            assertThat(routeBuilder.getUriRoutes())
                    .extracting(UriRoute::getHttpMethod)
                    .contains(HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.PUT);
            assertThat(routeBuilder.getUriRoutes().stream()
                            .filter(route -> route.getHttpMethod() == HttpMethod.GET)
                            .count())
                    .isEqualTo(2);
            assertThat(routeBuilder.getUriRoutes())
                    .allSatisfy(route -> assertThat(route.toRouteInfo().getDeclaringType())
                            .isEqualTo(Micronaut_routerTest.class));

            UriRoute detailRoute = routeBuilder.getUriRoutes().stream()
                    .filter(route -> route.getHttpMethod() == HttpMethod.GET)
                    .filter(route -> route.toRouteInfo().match(resourceBase + "/31").isPresent())
                    .findFirst()
                    .orElseThrow();
            assertThat(detailRoute.toRouteInfo().match(resourceBase + "/31").orElseThrow().invoke())
                    .isEqualTo("resource-31");

            UriRoute indexRoute = routeBuilder.getUriRoutes().stream()
                    .filter(route -> route.getHttpMethod() == HttpMethod.GET)
                    .filter(route -> route.toRouteInfo().match(resourceBase).isPresent())
                    .findFirst()
                    .orElseThrow();
            assertThat(indexRoute.toRouteInfo().match(resourceBase).orElseThrow().invoke())
                    .isEqualTo("resource-index");
        }
    }

    @Test
    @Timeout(55)
    void buildsAndExecutesProgrammaticStatusAndErrorRoutes() {
        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            ProgrammaticRouteBuilder routeBuilder = new ProgrammaticRouteBuilder(context);
            StatusRoute statusRoute = routeBuilder.status(
                    Micronaut_routerTest.class,
                    HttpStatus.ACCEPTED,
                    Micronaut_routerTest.class,
                    "programmaticStatus");
            ErrorRoute errorRoute = routeBuilder.error(
                    Micronaut_routerTest.class,
                    IllegalStateException.class,
                    Micronaut_routerTest.class,
                    "programmaticFailure",
                    IllegalStateException.class);

            assertThat(statusRoute.originatingType()).isEqualTo(Micronaut_routerTest.class);
            assertThat(statusRoute.status() == HttpStatus.ACCEPTED).isTrue();
            RouteMatch<?> statusMatch = statusRoute
                    .toRouteInfo()
                    .match(Micronaut_routerTest.class, HttpStatus.ACCEPTED)
                    .orElseThrow();
            assertThat(statusMatch.execute()).isEqualTo("programmatic-accepted");

            IllegalStateException failure = new IllegalStateException("route failure");
            assertThat(errorRoute.originatingType()).isEqualTo(Micronaut_routerTest.class);
            assertThat(errorRoute.exceptionType()).isEqualTo(IllegalStateException.class);
            RouteMatch<?> errorMatch = errorRoute
                    .toRouteInfo()
                    .match(Micronaut_routerTest.class, failure)
                    .orElseThrow();
            assertThat(errorMatch).isInstanceOf(MethodBasedRouteMatch.class);
            MethodBasedRouteMatch<?, ?> methodBasedErrorMatch = (MethodBasedRouteMatch<?, ?>) errorMatch;
            assertThat(methodBasedErrorMatch.invoke()).isEqualTo("handled-route failure");
        }
    }

    @Get(uri = "/items/{id}", produces = MediaType.TEXT_PLAIN)
    public String item(@PathVariable("id") long id) {
        return "item-" + id;
    }

    @Get(uri = "/items/featured", produces = MediaType.TEXT_PLAIN)
    public String featuredItem() {
        return "featured-item";
    }

    @Post(uri = "/items", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
    public String createItem(@Body String body) {
        return "created-" + body;
    }

    @Get(uri = "/search", produces = MediaType.TEXT_PLAIN)
    public String search(
            @QueryValue("term") String term,
            @Header("X-Trace") String trace,
            @RequestAttribute("tenant") String tenant) {
        return term + "|" + trace + "|" + tenant;
    }

    @Get(uri = "/versioned", produces = MediaType.TEXT_PLAIN)
    @Version("v1")
    public String versionOne() {
        return "version-one";
    }

    @Get(uri = "/versioned", produces = MediaType.TEXT_PLAIN)
    @Version("v2")
    public String versionTwo() {
        return "version-two";
    }

    @Error(status = HttpStatus.NOT_FOUND)
    public String notFound() {
        return "catalog-not-found";
    }

    @Error(IllegalArgumentException.class)
    public String invalidItem(IllegalArgumentException failure) {
        return "error: " + failure.getMessage();
    }

    @Executable
    public String programmaticCatalog(String category) {
        return "catalog-" + category;
    }

    @Executable
    public String programmaticDetails(String category, int item) {
        return category + "-" + item;
    }

    @Executable
    public String programmaticStatus() {
        return "programmatic-accepted";
    }

    @Executable
    public String programmaticFailure(IllegalStateException failure) {
        return "handled-" + failure.getMessage();
    }

    @Executable
    public String index() {
        return "resource-index";
    }

    @Executable
    public String show(Object id) {
        return "resource-" + id;
    }

    @Executable
    public String save() {
        return "resource-saved";
    }

    @Executable
    public String update(Object id) {
        return "resource-updated-" + id;
    }

    @Executable
    public String delete(Object id) {
        return "resource-deleted-" + id;
    }

    private static UriRouteMatch<?, ?> findClosest(Router router, HttpRequest<?> request) {
        UriRouteMatch<?, ?> routeMatch = router.findClosest(request);
        assertThat(routeMatch).isNotNull();
        return routeMatch;
    }

    @ServerFilter("/filtered/**")
    public static final class CatalogServerFilter {
        @RequestFilter(methods = HttpMethod.GET)
        public void filter() {
        }
    }

    public static final class OrderHistoryController {
    }

    public static final class ProgrammaticRouteBuilder extends DefaultRouteBuilder {
        public ProgrammaticRouteBuilder(ExecutionHandleLocator executionHandleLocator) {
            super(executionHandleLocator);
        }
    }
}
