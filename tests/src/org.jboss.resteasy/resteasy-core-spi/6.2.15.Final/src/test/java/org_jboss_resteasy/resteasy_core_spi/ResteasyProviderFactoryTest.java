/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.SeBootstrap;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.client.RxInvoker;
import jakarta.ws.rs.client.RxInvokerProvider;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.Variant;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.RuntimeDelegate;
import jakarta.ws.rs.ext.WriterInterceptor;

import org.jboss.resteasy.spi.AsyncClientResponseProvider;
import org.jboss.resteasy.spi.AsyncResponseProvider;
import org.jboss.resteasy.spi.AsyncStreamProvider;
import org.jboss.resteasy.spi.ContextInjector;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.InjectorFactory;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.StringParameterUnmarshaller;
import org.jboss.resteasy.spi.interception.JaxrsInterceptorRegistry;
import org.jboss.resteasy.spi.metadata.ResourceBuilder;
import org.jboss.resteasy.spi.statistics.StatisticsController;
import org.junit.jupiter.api.Test;

public class ResteasyProviderFactoryTest {
    @Test
    void newInstanceLoadsAndConstructsProviderFactoryFromContextClassLoader() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ProviderFactoryClassLoader(originalContextClassLoader));

            ResteasyProviderFactory factory = ResteasyProviderFactory.newInstance();

            assertThat(factory).isInstanceOf(TestProviderFactory.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static class ProviderFactoryClassLoader extends ClassLoader {
        ProviderFactoryClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if ("org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl".equals(name)) {
                return TestProviderFactory.class;
            }
            return super.loadClass(name);
        }
    }

    public static class TestProviderFactory extends ResteasyProviderFactory {
        public TestProviderFactory() {
        }

        @Override
        public Set<DynamicFeature> getServerDynamicFeatures() {
            return Collections.emptySet();
        }

        @Override
        public Set<DynamicFeature> getClientDynamicFeatures() {
            return Collections.emptySet();
        }

        @Override
        public Map<Class<?>, AsyncResponseProvider> getAsyncResponseProviders() {
            return Collections.emptyMap();
        }

        @Override
        public Map<Class<?>, AsyncClientResponseProvider> getAsyncClientResponseProviders() {
            return Collections.emptyMap();
        }

        @Override
        public Map<Class<?>, AsyncStreamProvider> getAsyncStreamProviders() {
            return Collections.emptyMap();
        }

        @Override
        public Map<Type, ContextInjector> getContextInjectors() {
            return Collections.emptyMap();
        }

        @Override
        public Map<Type, ContextInjector> getAsyncContextInjectors() {
            return Collections.emptyMap();
        }

        @Override
        public Set<Class<?>> getProviderClasses() {
            return Collections.emptySet();
        }

        @Override
        public Set<Object> getProviderInstances() {
            return Collections.emptySet();
        }

        @Override
        public <T> T getContextData(Class<T> type) {
            return null;
        }

        @Override
        public <T> T getContextData(Class<T> rawType, Type genericType, Annotation[] annotations,
                boolean unwrapAsync) {
            return null;
        }

        @Override
        protected void registerBuiltin() {
        }

        @Override
        public boolean isRegisterBuiltins() {
            return false;
        }

        @Override
        public void setRegisterBuiltins(boolean registerBuiltins) {
        }

        @Override
        public InjectorFactory getInjectorFactory() {
            return null;
        }

        @Override
        public void setInjectorFactory(InjectorFactory injectorFactory) {
        }

        @Override
        public JaxrsInterceptorRegistry<ReaderInterceptor> getServerReaderInterceptorRegistry() {
            return null;
        }

        @Override
        public JaxrsInterceptorRegistry<WriterInterceptor> getServerWriterInterceptorRegistry() {
            return null;
        }

        @Override
        public JaxrsInterceptorRegistry<ContainerRequestFilter> getContainerRequestFilterRegistry() {
            return null;
        }

        @Override
        public JaxrsInterceptorRegistry<ContainerResponseFilter> getContainerResponseFilterRegistry() {
            return null;
        }

        @Override
        public JaxrsInterceptorRegistry<ReaderInterceptor> getClientReaderInterceptorRegistry() {
            return null;
        }

        @Override
        public JaxrsInterceptorRegistry<WriterInterceptor> getClientWriterInterceptorRegistry() {
            return null;
        }

        @Override
        public JaxrsInterceptorRegistry<ClientRequestFilter> getClientRequestFilterRegistry() {
            return null;
        }

        @Override
        public JaxrsInterceptorRegistry<ClientResponseFilter> getClientResponseFilters() {
            return null;
        }

        @Override
        public boolean isBuiltinsRegistered() {
            return false;
        }

        @Override
        public void setBuiltinsRegistered(boolean builtinsRegistered) {
        }

        @Override
        public void addHeaderDelegate(Class clazz, RuntimeDelegate.HeaderDelegate header) {
        }

        @Override
        public <T> MessageBodyReader<T> getServerMessageBodyReader(Class<T> type, Type genericType,
                Annotation[] annotations, MediaType mediaType) {
            return null;
        }

        @Override
        public <T> MessageBodyReader<T> getClientMessageBodyReader(Class<T> type, Type genericType,
                Annotation[] annotations, MediaType mediaType) {
            return null;
        }

        @Override
        public List<ContextResolver> getContextResolvers(Class<?> clazz, MediaType type) {
            return Collections.emptyList();
        }

        @Override
        public ParamConverter getParamConverter(Class clazz, Type genericType, Annotation[] annotations) {
            return null;
        }

        @Override
        public <T> StringParameterUnmarshaller<T> createStringParameterUnmarshaller(Class<T> clazz) {
            return null;
        }

        @Override
        public void registerProvider(Class provider) {
        }

        @Override
        public String toString(Object object, Class clazz, Type genericType, Annotation[] annotations) {
            return String.valueOf(object);
        }

        @Override
        public RuntimeDelegate.HeaderDelegate getHeaderDelegate(Class<?> aClass) {
            return null;
        }

        @Override
        public void registerProvider(Class provider, boolean isBuiltin) {
        }

        @Override
        public void registerProvider(Class provider, Integer priorityOverride, boolean isBuiltin,
                Map<Class<?>, Integer> contracts) {
        }

        @Override
        public void registerProviderInstance(Object provider) {
        }

        @Override
        public void registerProviderInstance(Object provider, Map<Class<?>, Integer> contracts,
                Integer priorityOverride, boolean builtIn) {
        }

        @Override
        public <T> AsyncResponseProvider<T> getAsyncResponseProvider(Class<T> type) {
            return null;
        }

        @Override
        public <T> AsyncClientResponseProvider<T> getAsyncClientResponseProvider(Class<T> type) {
            return null;
        }

        @Override
        public <T> AsyncStreamProvider<T> getAsyncStreamProvider(Class<T> type) {
            return null;
        }

        @Override
        public MediaType getConcreteMediaTypeFromMessageBodyWriters(Class<?> type, Type genericType,
                Annotation[] annotations, MediaType mediaType) {
            return mediaType;
        }

        @Override
        public Map<MessageBodyWriter<?>, Class<?>> getPossibleMessageBodyWritersMap(Class type, Type genericType,
                Annotation[] annotations, MediaType accept) {
            return Collections.emptyMap();
        }

        @Override
        public <T> MessageBodyWriter<T> getServerMessageBodyWriter(Class<T> type, Type genericType,
                Annotation[] annotations, MediaType mediaType) {
            return null;
        }

        @Override
        public <T> MessageBodyWriter<T> getClientMessageBodyWriter(Class<T> type, Type genericType,
                Annotation[] annotations, MediaType mediaType) {
            return null;
        }

        @Override
        public <T> T createProviderInstance(Class<? extends T> clazz) {
            return null;
        }

        @Override
        public <T> T injectedInstance(Class<? extends T> clazz) {
            return null;
        }

        @Override
        public <T> T injectedInstance(Class<? extends T> clazz, HttpRequest request, HttpResponse response) {
            return null;
        }

        @Override
        public void injectProperties(Object obj) {
        }

        @Override
        public void injectProperties(Object obj, HttpRequest request, HttpResponse response) {
        }

        @Override
        public Map<String, Object> getMutableProperties() {
            return Collections.emptyMap();
        }

        @Override
        public ResteasyProviderFactory setProperties(Map<String, Object> properties) {
            return this;
        }

        @Override
        public Collection<Feature> getEnabledFeatures() {
            return Collections.emptyList();
        }

        @Override
        public <I extends RxInvoker> RxInvokerProvider<I> getRxInvokerProvider(Class<I> clazz) {
            return null;
        }

        @Override
        public RxInvokerProvider<?> getRxInvokerProviderFromReactiveClass(Class<?> clazz) {
            return null;
        }

        @Override
        public boolean isReactive(Class<?> clazz) {
            return false;
        }

        @Override
        public ResourceBuilder getResourceBuilder() {
            return null;
        }

        @Override
        public void initializeClientProviders(ResteasyProviderFactory factory) {
        }

        @Override
        public StatisticsController getStatisticsController() {
            return null;
        }

        @Override
        protected boolean isOnServer() {
            return false;
        }

        @Override
        public String toHeaderString(Object object) {
            return String.valueOf(object);
        }

        @Override
        public UriBuilder createUriBuilder() {
            return null;
        }

        @Override
        public Response.ResponseBuilder createResponseBuilder() {
            return null;
        }

        @Override
        public Variant.VariantListBuilder createVariantListBuilder() {
            return null;
        }

        @Override
        public <T> T createEndpoint(Application application, Class<T> endpointType) {
            return null;
        }

        @Override
        public <T> RuntimeDelegate.HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
            return null;
        }

        @Override
        public Link.Builder createLinkBuilder() {
            return null;
        }

        @Override
        public SeBootstrap.Configuration.Builder createConfigurationBuilder() {
            return null;
        }

        @Override
        public CompletionStage<SeBootstrap.Instance> bootstrap(Application application,
                SeBootstrap.Configuration configuration) {
            return null;
        }

        @Override
        public CompletionStage<SeBootstrap.Instance> bootstrap(Class<? extends Application> applicationClass,
                SeBootstrap.Configuration configuration) {
            return null;
        }

        @Override
        public EntityPart.Builder createEntityPartBuilder(String partName) {
            return null;
        }

        @Override
        public Configuration getConfiguration() {
            return this;
        }

        @Override
        public ResteasyProviderFactory property(String name, Object value) {
            return this;
        }

        @Override
        public ResteasyProviderFactory register(Class<?> componentClass) {
            return this;
        }

        @Override
        public ResteasyProviderFactory register(Class<?> componentClass, int priority) {
            return this;
        }

        @Override
        public ResteasyProviderFactory register(Class<?> componentClass, Class<?>... contracts) {
            return this;
        }

        @Override
        public ResteasyProviderFactory register(Class<?> componentClass, Map<Class<?>, Integer> contracts) {
            return this;
        }

        @Override
        public ResteasyProviderFactory register(Object component) {
            return this;
        }

        @Override
        public ResteasyProviderFactory register(Object component, int priority) {
            return this;
        }

        @Override
        public ResteasyProviderFactory register(Object component, Class<?>... contracts) {
            return this;
        }

        @Override
        public ResteasyProviderFactory register(Object component, Map<Class<?>, Integer> contracts) {
            return this;
        }

        @Override
        public RuntimeType getRuntimeType() {
            return RuntimeType.SERVER;
        }

        @Override
        public Map<String, Object> getProperties() {
            return Collections.emptyMap();
        }

        @Override
        public Object getProperty(String name) {
            return null;
        }

        @Override
        public Collection<String> getPropertyNames() {
            return Collections.emptyList();
        }

        @Override
        public boolean isEnabled(Feature feature) {
            return false;
        }

        @Override
        public boolean isEnabled(Class<? extends Feature> featureClass) {
            return false;
        }

        @Override
        public boolean isRegistered(Object component) {
            return false;
        }

        @Override
        public boolean isRegistered(Class<?> componentClass) {
            return false;
        }

        @Override
        public Map<Class<?>, Integer> getContracts(Class<?> componentClass) {
            return Collections.emptyMap();
        }

        @Override
        public Set<Class<?>> getClasses() {
            return Collections.emptySet();
        }

        @Override
        public Set<Object> getInstances() {
            return Collections.emptySet();
        }

        @Override
        public <T> MessageBodyReader<T> getMessageBodyReader(Class<T> type, Type genericType,
                Annotation[] annotations, MediaType mediaType) {
            return null;
        }

        @Override
        public <T> MessageBodyWriter<T> getMessageBodyWriter(Class<T> type, Type genericType,
                Annotation[] annotations, MediaType mediaType) {
            return null;
        }

        @Override
        public <T extends Throwable> ExceptionMapper<T> getExceptionMapper(Class<T> type) {
            return null;
        }

        @Override
        public <T> ContextResolver<T> getContextResolver(Class<T> contextType, MediaType mediaType) {
            return null;
        }
    }
}
