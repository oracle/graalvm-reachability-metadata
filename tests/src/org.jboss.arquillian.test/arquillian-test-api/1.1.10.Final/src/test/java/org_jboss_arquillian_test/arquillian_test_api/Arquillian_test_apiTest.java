/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.jupiter.api.Test;

public class Arquillian_test_apiTest {
    @Test
    public void annotationContractAdvertisesRuntimeFieldAndParameterUsage() {
        Retention retention = annotation(ArquillianResource.class, Retention.class);
        Target target = annotation(ArquillianResource.class, Target.class);

        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(EnumSet.copyOf(Arrays.asList(target.value())))
                .containsExactlyInAnyOrder(ElementType.FIELD, ElementType.PARAMETER);
        assertThat(ArquillianResource.class).isNotNull()
                .hasAnnotation(Documented.class)
                .hasAnnotation(Inherited.class);
    }

    @Test
    public void fieldResourceCanUseFieldTypeAsDefaultLookupKey() throws Exception {
        Field field = ResourceConsumer.class.getField("defaultUrlResource");
        ArquillianResource resource = annotation(field, ArquillianResource.class);

        assertThat(resource).isNotNull();
        assertThat(resource.value()).isEqualTo(ArquillianResource.class);
        assertThat(resource.annotationType()).isEqualTo(ArquillianResource.class);
        assertThat(resolveFieldResourceType(field, resource)).isEqualTo(URL.class);
    }

    @Test
    public void fieldResourceCanOverrideLookupKeyWithExplicitType() throws Exception {
        Field field = ResourceConsumer.class.getField("explicitUriResource");
        ArquillianResource resource = annotation(field, ArquillianResource.class);

        assertThat(resource).isNotNull();
        assertThat(resource.value()).isEqualTo(URI.class);
        assertThat(resolveFieldResourceType(field, resource)).isEqualTo(URI.class);
    }

    @Test
    public void methodParametersKeepIndependentResourceLookupKeys() throws Exception {
        Method method = ResourceConsumer.class.getMethod("configure", URL.class, URI.class, String.class);
        Parameter[] parameters = method.getParameters();

        Map<Class<?>, Class<?>> resourceLookupPlan = new LinkedHashMap<>();
        for (Parameter parameter : parameters) {
            ArquillianResource resource = annotation(parameter, ArquillianResource.class);
            resourceLookupPlan.put(parameter.getType(), resolveParameterResourceType(parameter, resource));
        }

        assertThat(resourceLookupPlan)
                .containsEntry(URL.class, URL.class)
                .containsEntry(URI.class, URI.class)
                .containsEntry(String.class, String.class);
    }

    @Test
    public void annotatedMembersCanBeResolvedIntoResourceValuesUsingPublicContract() throws Exception {
        ResourceConsumer consumer = new ResourceConsumer();
        Map<Class<?>, Object> resources = new LinkedHashMap<>();
        URL url = URI.create("https://example.test/arquillian").toURL();
        URI uri = URI.create("urn:arquillian:test-api");
        String text = "configured-resource";
        resources.put(URL.class, url);
        resources.put(URI.class, uri);
        resources.put(String.class, text);

        injectAnnotatedFields(consumer, resources);
        Object[] arguments = resolveAnnotatedArguments(
                ResourceConsumer.class.getMethod("configure", URL.class, URI.class, String.class), resources);
        consumer.configure((URL) arguments[0], (URI) arguments[1], (String) arguments[2]);

        assertThat(consumer.defaultUrlResource).isSameAs(url);
        assertThat(consumer.explicitUriResource).isSameAs(uri);
        assertThat(consumer.configuredUrl).isSameAs(url);
        assertThat(consumer.configuredUri).isSameAs(uri);
        assertThat(consumer.configuredText).isSameAs(text);
    }

    @Test
    public void constructorParametersCanDeclareResourcesWithoutChangingConstruction() {
        URI deploymentUri = URI.create("urn:arquillian:constructor-resource");
        String deploymentName = "constructor-resource";

        ConstructorResourceConsumer consumer = new ConstructorResourceConsumer(deploymentUri, deploymentName);

        assertThat(consumer.deploymentUri()).isSameAs(deploymentUri);
        assertThat(consumer.deploymentName()).isSameAs(deploymentName);
    }

    @Test
    public void lambdaParametersCanDeclareResourcesWhileKeepingCallbackBehavior() throws Exception {
        ResourceCallback callback = (@ArquillianResource URL url, @ArquillianResource(URI.class) URI uri) ->
                url.getHost() + "|" + uri.getSchemeSpecificPart();

        URL url = URI.create("https://example.test/callback-resource").toURL();
        URI uri = URI.create("urn:arquillian:lambda-resource");

        assertThat(callback.describe(url, uri)).isEqualTo("example.test|arquillian:lambda-resource");
    }

    // Checkstyle: allow direct annotation access
    private static <A extends Annotation> A annotation(AnnotatedElement element, Class<A> annotationType) {
        AnnotatedElement elementAnnotationAccess = element;
        return elementAnnotationAccess.getAnnotation(annotationType);
    }
    // Checkstyle: disallow direct annotation access

    private static Class<?> resolveFieldResourceType(Field field, ArquillianResource resource) {
        if (resource.value().equals(ArquillianResource.class)) {
            return field.getType();
        }
        return resource.value();
    }

    private static Class<?> resolveParameterResourceType(Parameter parameter, ArquillianResource resource) {
        if (resource.value().equals(ArquillianResource.class)) {
            return parameter.getType();
        }
        return resource.value();
    }

    private static void injectAnnotatedFields(ResourceConsumer consumer, Map<Class<?>, Object> resources)
            throws IllegalAccessException {
        for (Field field : ResourceConsumer.class.getFields()) {
            ArquillianResource resource = annotation(field, ArquillianResource.class);
            if (resource != null) {
                field.set(consumer, resources.get(resolveFieldResourceType(field, resource)));
            }
        }
    }

    private static Object[] resolveAnnotatedArguments(Method method, Map<Class<?>, Object> resources) {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            ArquillianResource resource = parameters[i].getAnnotation(ArquillianResource.class);
            arguments[i] = resources.get(resolveParameterResourceType(parameters[i], resource));
        }
        return arguments;
    }

    @FunctionalInterface
    private interface ResourceCallback {
        String describe(@ArquillianResource URL url, @ArquillianResource(URI.class) URI uri);
    }

    public static final class ResourceConsumer {
        @ArquillianResource
        public URL defaultUrlResource;

        @ArquillianResource(URI.class)
        public URI explicitUriResource;

        private URL configuredUrl;
        private URI configuredUri;
        private String configuredText;

        public void configure(@ArquillianResource URL url,
                @ArquillianResource(URI.class) URI uri,
                @ArquillianResource String text) {
            configuredUrl = url;
            configuredUri = uri;
            configuredText = text;
        }
    }

    public static final class ConstructorResourceConsumer {
        private final URI deploymentUri;
        private final String deploymentName;

        public ConstructorResourceConsumer(@ArquillianResource(URI.class) URI deploymentUri,
                @ArquillianResource String deploymentName) {
            this.deploymentUri = deploymentUri;
            this.deploymentName = deploymentName;
        }

        public URI deploymentUri() {
            return deploymentUri;
        }

        public String deploymentName() {
            return deploymentName;
        }
    }
}
