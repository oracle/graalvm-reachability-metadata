/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_views.micronaut_views_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.Writable;
import io.micronaut.core.io.value.StringResourceLoader;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.views.AbstractViewsRenderer;
import io.micronaut.views.ModelAndView;
import io.micronaut.views.ModelAndViewRenderer;
import io.micronaut.views.View;
import io.micronaut.views.ViewUtils;
import io.micronaut.views.ViewsConfigurationProperties;
import io.micronaut.views.ViewsRenderer;
import io.micronaut.views.ViewsRendererConfiguration;
import io.micronaut.views.ViewsRendererLocator;
import io.micronaut.views.ViewsResolver;
import io.micronaut.views.csp.CspConfiguration;
import io.micronaut.views.csp.CspFilter;
import io.micronaut.views.http.RawModelAndViewMessageBodyHandler;
import io.micronaut.views.http.ResponseBodySwap;
import io.micronaut.views.http.ResponseBodySwapper;
import io.micronaut.views.http.ViewsFilterConfiguration;
import io.micronaut.views.model.ViewModelProcessor;
import jakarta.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/** Integration coverage for the engine-neutral Micronaut Views APIs. */
@Singleton
@Timeout(55)
public class Micronaut_views_coreTest {
    @Test
    void applicationContextBindsViewsFilterAndCspConfiguration() {
        Map<String, Object> properties = Map.of(
                "micronaut.views.enabled", true,
                "micronaut.views.folder", "mail\\templates",
                "micronaut.views.filter.enabled", false,
                "micronaut.views.csp.enabled", true,
                "micronaut.views.csp.policy-directives", "default-src 'self'",
                "micronaut.views.csp.report-only", true,
                "micronaut.views.csp.generate-nonce", true,
                "micronaut.views.csp.filter-path", "/pages/**");

        try (ApplicationContext context = ApplicationContext.run(properties, Environment.TEST)) {
            ViewsConfigurationProperties views = context.getBean(ViewsConfigurationProperties.class);
            ViewsFilterConfiguration filter = context.getBean(ViewsFilterConfiguration.class);
            CspConfiguration csp = context.getBean(CspConfiguration.class);

            assertThat(views.isEnabled()).isTrue();
            assertThat(views.getFolder()).isEqualTo("mail/templates/");
            assertThat(filter.isEnabled()).isFalse();
            assertThat(csp.isEnabled()).isTrue();
            assertThat(csp.getPolicyDirectives()).contains("default-src 'self'");
            assertThat(csp.isReportOnly()).isTrue();
            assertThat(csp.isNonceEnabled()).isTrue();
            assertThat(csp.getFilterPath()).isEqualTo("/pages/**");
        }
    }

    @Test
    void resolvesAnnotatedAndExplicitViewsAndSwapsResponseBodies() {
        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            BeanDefinition<Micronaut_views_coreTest> definition =
                    context.getBeanDefinition(Micronaut_views_coreTest.class);
            ExecutableMethod<Micronaut_views_coreTest, GreetingModel> annotatedRoute =
                    definition.getRequiredMethod("annotatedResponse");
            ExecutableMethod<Micronaut_views_coreTest, ModelAndView<GreetingModel>> explicitRoute =
                    definition.getRequiredMethod("explicitResponse");
            HttpRequest<?> request = HttpRequest.GET("/greeting").accept(MediaType.TEXT_HTML_TYPE);
            GreetingModel annotatedModel = new GreetingModel("Ada");
            MutableHttpResponse<GreetingModel> annotatedResponse = HttpResponse.ok(annotatedModel);
            annotatedResponse.attribute(HttpAttributes.ROUTE_MATCH, annotatedRoute);

            ViewsResolver resolver = context.getBean(ViewsResolver.class);
            assertThat(resolver.resolveView(request, annotatedResponse))
                    .contains("annotated-greeting");

            ResponseBodySwapper<?> swapper = context.getBean(ResponseBodySwapper.class);
            ResponseBodySwap<?> annotatedSwap =
                    swapper.swap(request, annotatedResponse).orElseThrow();
            assertThat(annotatedSwap.mediaType()).isEqualTo(MediaType.TEXT_HTML);
            assertThat(annotatedSwap.body()).isInstanceOf(ModelAndView.class);
            ModelAndView<?> annotatedModelAndView = (ModelAndView<?>) annotatedSwap.body();
            assertThat(annotatedModelAndView.getView()).contains("annotated-greeting");
            assertThat(annotatedModelAndView.getModel().orElseThrow())
                    .isSameAs(annotatedModel);

            ModelAndView<GreetingModel> explicitModelAndView =
                    new ModelAndView<>("explicit-greeting", new GreetingModel("Grace"));
            MutableHttpResponse<ModelAndView<GreetingModel>> explicitResponse =
                    HttpResponse.ok(explicitModelAndView);
            explicitResponse.attribute(HttpAttributes.ROUTE_MATCH, explicitRoute);

            assertThat(resolver.resolveView(request, explicitResponse))
                    .contains("explicit-greeting");
            ResponseBodySwap<?> explicitSwap =
                    swapper.swap(request, explicitResponse).orElseThrow();
            assertThat(explicitSwap.body()).isSameAs(explicitModelAndView);
            assertThat(explicitSwap.mediaType()).isEqualTo(MediaType.TEXT_HTML);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void locatesDecoratesRendersAndWritesModelAndView() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            ViewsRendererLocator locator = context.getBean(ViewsRendererLocator.class);
            GreetingModel model = new GreetingModel("Ada");
            ViewsRenderer<?, ?> located = locator
                    .resolveViewsRenderer("greeting", MediaType.TEXT_HTML, model)
                    .orElseThrow();
            assertThat(located).isInstanceOf(GreetingViewsRenderer.class);

            ModelAndViewRenderer<GreetingModel, HttpRequest<?>> renderer =
                    (ModelAndViewRenderer<GreetingModel, HttpRequest<?>>)
                            context.getBean(ModelAndViewRenderer.class);
            HttpRequest<?> request = HttpRequest.GET("/rendered/greeting");
            ModelAndView<GreetingModel> modelAndView = new ModelAndView<>("greeting", model);

            Writable writable = renderer
                    .render(modelAndView, request, "text/html; charset=UTF-8")
                    .orElseThrow();
            assertThat(writeToString(writable))
                    .isEqualTo("Hello Ada from /rendered/greeting");
            assertThat(model.getDecoratedPath()).isEqualTo("/rendered/greeting");

            RawModelAndViewMessageBodyHandler<GreetingModel> handler =
                    new RawModelAndViewMessageBodyHandler<>(renderer);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ModelAndView<GreetingModel> secondModelAndView =
                    new ModelAndView<>("greeting", new GreetingModel("Grace"));
            handler.writePieceTo(output, request, MediaType.TEXT_HTML_TYPE, secondModelAndView);

            assertThat(handler.isBlocking()).isTrue();
            assertThat(handler.getType().getType()).isEqualTo(ModelAndView.class);
            assertThat(output.toString(StandardCharsets.UTF_8))
                    .isEqualTo("Hello Grace from /rendered/greeting");
        }
    }

    @Test
    void resourceHelpersNormalizeReadAndLocateTemplates() throws IOException {
        ResourceLoader stringResources = StringResourceLoader.getInstance();
        String template = "<h1>Hello Gr\u00fc\u00dfe</h1>";

        assertThat(ViewUtils.normalizeFolder("\\mail\\views"))
                .isEqualTo("mail/views/");
        assertThat(ViewUtils.normalizeFile("/mail\\welcome.html", ".html"))
                .isEqualTo("mail/welcome");
        assertThat(ViewUtils.readResourceAsString(
                        stringResources, "string:" + template, StandardCharsets.UTF_8))
                .isEqualTo(template);

        ResourceBackedRenderer renderer = new ResourceBackedRenderer(stringResources);
        assertThat(renderer.templateName("welcome.html")).isEqualTo("welcome.html");
        assertThat(renderer.templateLocation("welcome")).isEqualTo("string:welcome.html");
        assertThat(renderer.exists("welcome")).isTrue();
        assertThat(writeToString(renderer.render("welcome", null, null)))
                .isEqualTo("welcome.html");

        GreetingModel model = new GreetingModel("Lin");
        assertThat(ViewUtils.modelOf(model)).containsEntry("name", "Lin");
        Map<String, Object> suppliedMap = Map.of("name", "Maya");
        assertThat(ViewUtils.modelOf(suppliedMap)).isSameAs(suppliedMap);
        assertThat(ViewUtils.modelOf(null)).isEmpty();
    }

    @Test
    void cspFilterAddsNonceToRequestAndResponsePolicy() {
        CspConfiguration configuration = new CspConfiguration();
        configuration.setEnabled(true);
        configuration.setGenerateNonce(true);
        configuration.setReportOnly(false);
        configuration.setRandomEngine(new Random(42));
        configuration.setPolicyDirectives("script-src 'nonce-{#nonceValue}'");

        CspFilter filter = new CspFilter(configuration);
        ImmediateServerFilterChain chain = new ImmediateServerFilterChain();
        CollectingSubscriber subscriber = new CollectingSubscriber();
        filter.doFilter(HttpRequest.GET("/secured-view"), chain).subscribe(subscriber);

        assertThat(subscriber.getFailure()).isNull();
        assertThat(subscriber.isComplete()).isTrue();
        MutableHttpResponse<?> response = subscriber.getResponse();
        assertThat(response).isNotNull();
        String nonce = chain.getRequest()
                .getAttribute(CspFilter.NONCE_PROPERTY, String.class)
                .orElseThrow();
        assertThat(Base64.getDecoder().decode(nonce)).hasSize(CspConfiguration.NONCE_LENGTH);
        assertThat(response.getHeaders().get(CspFilter.CSP_HEADER))
                .isEqualTo("script-src 'nonce-" + nonce + "'");
    }

    @Executable
    @View("annotated-greeting")
    @Produces(MediaType.TEXT_HTML)
    public GreetingModel annotatedResponse() {
        return new GreetingModel("Ada");
    }

    @Executable
    public ModelAndView<GreetingModel> explicitResponse() {
        return new ModelAndView<>("explicit-greeting", new GreetingModel("Grace"));
    }

    private static String writeToString(Writable writable) throws IOException {
        StringWriter writer = new StringWriter();
        writable.writeTo(writer);
        return writer.toString();
    }

    @Introspected
    public static final class GreetingModel {
        private final String name;
        private String decoratedPath;

        public GreetingModel(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String getDecoratedPath() {
            return decoratedPath;
        }

        public void setDecoratedPath(String decoratedPath) {
            this.decoratedPath = decoratedPath;
        }
    }

    @Singleton
    @Produces(MediaType.TEXT_HTML)
    public static final class GreetingViewsRenderer
            implements ViewsRenderer<GreetingModel, HttpRequest<?>> {
        @Override
        public Writable render(String view, GreetingModel model, HttpRequest<?> request) {
            return new TextWritable(
                    "Hello " + model.getName() + " from " + model.getDecoratedPath());
        }

        @Override
        public boolean exists(String view) {
            return "greeting".equals(view);
        }
    }

    @Singleton
    public static final class GreetingModelProcessor
            implements ViewModelProcessor<GreetingModel, HttpRequest<?>> {
        @Override
        public void process(
                HttpRequest<?> request, ModelAndView<GreetingModel> modelAndView) {
            modelAndView.getModel().orElseThrow().setDecoratedPath(request.getPath());
        }
    }

    private static final class TextWritable implements Writable {
        private final String value;

        private TextWritable(String value) {
            this.value = value;
        }

        @Override
        public void writeTo(Writer writer) throws IOException {
            writer.write(value);
        }
    }

    private static final class ResourceBackedRenderer
            extends AbstractViewsRenderer<Object, Object> {
        private ResourceBackedRenderer(ResourceLoader resourceLoader) {
            super(new HtmlRendererConfiguration(), "string:", resourceLoader);
        }

        @Override
        public Writable render(String view, Object model, Object request) {
            return new TextWritable(getTemplate(view, StandardCharsets.UTF_8));
        }

        private String templateName(String view) {
            return viewNameWithExtension(view);
        }

        private String templateLocation(String view) {
            return viewLocationWithExtension(view);
        }
    }

    private static final class HtmlRendererConfiguration
            implements ViewsRendererConfiguration {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getDefaultExtension() {
            return ".html";
        }
    }

    private static final class ImmediateServerFilterChain implements ServerFilterChain {
        private HttpRequest<?> request;

        @Override
        public Publisher<MutableHttpResponse<?>> proceed(HttpRequest<?> request) {
            this.request = request;
            return new SingleResponsePublisher(HttpResponse.ok("secured"));
        }

        private HttpRequest<?> getRequest() {
            return request;
        }
    }

    private static final class SingleResponsePublisher
            implements Publisher<MutableHttpResponse<?>> {
        private final MutableHttpResponse<?> response;

        private SingleResponsePublisher(MutableHttpResponse<?> response) {
            this.response = response;
        }

        @Override
        public void subscribe(Subscriber<? super MutableHttpResponse<?>> subscriber) {
            subscriber.onSubscribe(new SingleResponseSubscription(subscriber, response));
        }
    }

    private static final class SingleResponseSubscription implements Subscription {
        private final Subscriber<? super MutableHttpResponse<?>> subscriber;
        private final MutableHttpResponse<?> response;
        private boolean delivered;

        private SingleResponseSubscription(
                Subscriber<? super MutableHttpResponse<?>> subscriber,
                MutableHttpResponse<?> response) {
            this.subscriber = subscriber;
            this.response = response;
        }

        @Override
        public void request(long count) {
            if (!delivered && count > 0) {
                delivered = true;
                subscriber.onNext(response);
                subscriber.onComplete();
            }
        }

        @Override
        public void cancel() {
            delivered = true;
        }
    }

    private static final class CollectingSubscriber
            implements Subscriber<MutableHttpResponse<?>> {
        private MutableHttpResponse<?> response;
        private Throwable failure;
        private boolean complete;

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(MutableHttpResponse<?> response) {
            this.response = response;
        }

        @Override
        public void onError(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public void onComplete() {
            complete = true;
        }

        private MutableHttpResponse<?> getResponse() {
            return response;
        }

        private Throwable getFailure() {
            return failure;
        }

        private boolean isComplete() {
            return complete;
        }
    }
}
