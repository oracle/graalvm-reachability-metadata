/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.spi_annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Spi_annotationsTest {
    @Test
    void endpointAnnotationExposesConfiguredValues() {
        UriEndpoint endpoint = getAnnotation(RichEndpoint.class, UriEndpoint.class);

        assertThat(endpoint.scheme()).isEqualTo("rich");
        assertThat(endpoint.extendsScheme()).isEqualTo("base");
        assertThat(endpoint.syntax()).isEqualTo("rich:account:operation");
        assertThat(endpoint.alternativeSyntax()).isEqualTo("rich:account/operation");
        assertThat(endpoint.firstVersion()).isEqualTo("3.0.0");
        assertThat(endpoint.consumerPrefix()).isEqualTo("consumer.");
        assertThat(endpoint.title()).isEqualTo("Rich endpoint");
        assertThat(endpoint.label()).isEqualTo("testing,core");
        assertThat(endpoint.producerOnly()).isTrue();
        assertThat(endpoint.consumerOnly()).isFalse();
        assertThat(endpoint.lenientProperties()).isTrue();
        assertThat(endpoint.generateConfigurer()).isFalse();
    }

    @Test
    void endpointAnnotationProvidesDocumentedDefaults() {
        UriEndpoint endpoint = getAnnotation(MinimalEndpoint.class, UriEndpoint.class);

        assertThat(endpoint.scheme()).isEqualTo("minimal");
        assertThat(endpoint.title()).isEqualTo("Minimal endpoint");
        assertThat(endpoint.syntax()).isEqualTo("minimal:path");
        assertThat(endpoint.extendsScheme()).isEmpty();
        assertThat(endpoint.alternativeSyntax()).isEmpty();
        assertThat(endpoint.firstVersion()).isEmpty();
        assertThat(endpoint.consumerPrefix()).isEmpty();
        assertThat(endpoint.label()).isEmpty();
        assertThat(endpoint.producerOnly()).isFalse();
        assertThat(endpoint.consumerOnly()).isFalse();
        assertThat(endpoint.lenientProperties()).isFalse();
        assertThat(endpoint.generateConfigurer()).isTrue();
    }

    @Test
    void uriParamsAndMetadataAnnotationsExposeTypeLevelValues() {
        UriParams params = getAnnotation(RichEndpoint.class, UriParams.class);
        Metadata metadata = getAnnotation(RichEndpoint.class, Metadata.class);

        assertThat(params.prefix()).isEqualTo("advanced.");
        assertThat(metadata.label()).isEqualTo("component");
        assertThat(metadata.defaultValue()).isEqualTo("enabled");
        assertThat(metadata.required()).isTrue();
        assertThat(metadata.title()).isEqualTo("Component metadata");
        assertThat(metadata.description()).isEqualTo("Metadata attached to the endpoint type");
    }

    @Test
    void endpointAnnotationCanDeclareConsumerOnlyEndpoint() {
        UriEndpoint endpoint = getAnnotation(ConsumerEndpoint.class, UriEndpoint.class);

        assertThat(endpoint.scheme()).isEqualTo("consume");
        assertThat(endpoint.syntax()).isEqualTo("consume:queue");
        assertThat(endpoint.title()).isEqualTo("Consumer endpoint");
        assertThat(endpoint.consumerOnly()).isTrue();
        assertThat(endpoint.producerOnly()).isFalse();
    }

    @Test
    void uriPathAnnotationExposesPathMetadata() throws NoSuchFieldException {
        Field accountField = RichEndpoint.class.getDeclaredField("account");
        UriPath path = getAnnotation(accountField, UriPath.class);

        assertThat(path.name()).isEqualTo("account");
        assertThat(path.defaultValue()).isEqualTo("primary");
        assertThat(path.defaultValueNote()).isEqualTo("Uses the primary account when omitted");
        assertThat(path.description()).isEqualTo("Account identifier used in the URI path");
        assertThat(path.enums()).isEqualTo("primary,secondary");
        assertThat(path.label()).isEqualTo("path,required");
        assertThat(path.javaType()).isEqualTo("java.lang.String");
    }

    @Test
    void uriParamAnnotationExposesParameterMetadata() throws NoSuchFieldException {
        Field flagsField = RichEndpoint.class.getDeclaredField("flags");
        UriParam param = getAnnotation(flagsField, UriParam.class);

        assertThat(param.name()).isEqualTo("flag");
        assertThat(param.defaultValue()).isEqualTo("fast");
        assertThat(param.defaultValueNote()).isEqualTo("Multiple flags may be supplied");
        assertThat(param.description()).isEqualTo("Runtime flags applied to the endpoint");
        assertThat(param.enums()).isEqualTo("fast,safe,verbose");
        assertThat(param.label()).isEqualTo("producer,advanced");
        assertThat(param.javaType()).isEqualTo("java.util.List<java.lang.String>");
        assertThat(param.multiValue()).isTrue();
        assertThat(param.prefix()).isEqualTo("flag.");
        assertThat(param.optionalPrefix()).isEqualTo("optional.");
    }

    @Test
    void fieldAndMethodMetadataAnnotationsExposeValues() throws NoSuchFieldException, NoSuchMethodException {
        Field descriptionField = RichEndpoint.class.getDeclaredField("description");
        Method buildUriMethod = RichEndpoint.class.getDeclaredMethod("buildUri");
        Metadata fieldMetadata = getAnnotation(descriptionField, Metadata.class);
        Metadata methodMetadata = getAnnotation(buildUriMethod, Metadata.class);

        assertThat(fieldMetadata.label()).isEqualTo("field");
        assertThat(fieldMetadata.defaultValue()).isEqualTo("n/a");
        assertThat(fieldMetadata.required()).isFalse();
        assertThat(fieldMetadata.title()).isEqualTo("Description");
        assertThat(fieldMetadata.description()).isEqualTo("Optional endpoint description");

        assertThat(methodMetadata.label()).isEqualTo("operation");
        assertThat(methodMetadata.defaultValue()).isEqualTo("rich:primary:status");
        assertThat(methodMetadata.required()).isTrue();
        assertThat(methodMetadata.title()).isEqualTo("URI builder");
        assertThat(methodMetadata.description()).isEqualTo("Builds the endpoint URI");
    }

    @Test
    void uriParamsAnnotationSupportsNestedConfigurationTypes() throws NoSuchFieldException {
        Field securityField = EndpointWithNestedConfiguration.class.getDeclaredField("security");
        UriParam securityParam = getAnnotation(securityField, UriParam.class);
        UriParams securityParams = getAnnotation(securityField.getType(), UriParams.class);
        Field tokenField = securityField.getType().getDeclaredField("token");
        Field retriesField = securityField.getType().getDeclaredField("retries");
        UriParam tokenParam = getAnnotation(tokenField, UriParam.class);
        UriParam retriesParam = getAnnotation(retriesField, UriParam.class);

        assertThat(securityField.getType()).isEqualTo(SecurityConfiguration.class);
        assertThat(securityParam.name()).isEqualTo("security");
        assertThat(securityParam.label()).isEqualTo("advanced,security");
        assertThat(securityParams.prefix()).isEqualTo("security.");

        assertThat(tokenParam.name()).isEqualTo("token");
        assertThat(tokenParam.description()).isEqualTo("Authentication token sent with endpoint requests");
        assertThat(tokenParam.label()).isEqualTo("security");
        assertThat(tokenParam.defaultValue()).isEqualTo("anonymous");

        assertThat(retriesParam.name()).isEqualTo("retries");
        assertThat(retriesParam.defaultValue()).isEqualTo("3");
        assertThat(retriesParam.javaType()).isEqualTo("int");
        assertThat(retriesParam.label()).isEqualTo("advanced");
    }

    @Test
    void optionalAnnotationsReturnEmptyDefaults() throws NoSuchFieldException {
        UriParams params = getAnnotation(MinimalEndpoint.class, UriParams.class);
        Metadata metadata = getAnnotation(MinimalEndpoint.class, Metadata.class);
        UriPath path = getAnnotation(MinimalEndpoint.class.getDeclaredField("path"), UriPath.class);
        UriParam param = getAnnotation(MinimalEndpoint.class.getDeclaredField("option"), UriParam.class);

        assertThat(params.prefix()).isEmpty();
        assertThat(metadata.label()).isEmpty();
        assertThat(metadata.defaultValue()).isEmpty();
        assertThat(metadata.required()).isFalse();
        assertThat(metadata.title()).isEmpty();
        assertThat(metadata.description()).isEmpty();

        assertThat(path.name()).isEmpty();
        assertThat(path.defaultValue()).isEmpty();
        assertThat(path.defaultValueNote()).isEmpty();
        assertThat(path.description()).isEmpty();
        assertThat(path.enums()).isEmpty();
        assertThat(path.label()).isEmpty();
        assertThat(path.javaType()).isEmpty();

        assertThat(param.name()).isEmpty();
        assertThat(param.defaultValue()).isEmpty();
        assertThat(param.defaultValueNote()).isEmpty();
        assertThat(param.description()).isEmpty();
        assertThat(param.enums()).isEmpty();
        assertThat(param.label()).isEmpty();
        assertThat(param.javaType()).isEmpty();
        assertThat(param.multiValue()).isFalse();
        assertThat(param.prefix()).isEmpty();
        assertThat(param.optionalPrefix()).isEmpty();
    }

    @Test
    void annotationTypesDeclareRuntimeRetentionAndSupportedTargets() {
        assertRuntimeDocumentedTargets(UriEndpoint.class, ElementType.TYPE);
        assertRuntimeDocumentedTargets(UriParams.class, ElementType.TYPE);
        assertRuntimeDocumentedTargets(UriPath.class, ElementType.FIELD);
        assertRuntimeDocumentedTargets(UriParam.class, ElementType.FIELD);
        assertRuntimeDocumentedTargets(Metadata.class, ElementType.TYPE, ElementType.METHOD, ElementType.FIELD);
    }

    private static void assertRuntimeDocumentedTargets(
            Class<?> annotationType,
            ElementType firstExpectedTarget,
            ElementType... additionalExpectedTargets) {
        ElementType[] expectedTargets = expectedTargets(firstExpectedTarget, additionalExpectedTargets);

        assertThat(annotationType).isAnnotation();
        assertThat(getAnnotation(annotationType, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(isAnnotationPresent(annotationType, Documented.class)).isTrue();
        assertThat(getAnnotation(annotationType, Target.class).value()).containsExactly(expectedTargets);
    }

    private static <A extends Annotation> A getAnnotation(
            AnnotatedElement annotatedElementAnnotationAccess,
            Class<A> annotationType) {
        return annotatedElementAnnotationAccess.getAnnotation(annotationType);
    }

    private static boolean isAnnotationPresent(
            AnnotatedElement annotatedElementAnnotationAccess,
            Class<? extends Annotation> annotationType) {
        return annotatedElementAnnotationAccess.isAnnotationPresent(annotationType);
    }

    private static ElementType[] expectedTargets(
            ElementType firstExpectedTarget,
            ElementType... additionalExpectedTargets) {
        ElementType[] expectedTargets = new ElementType[additionalExpectedTargets.length + 1];
        expectedTargets[0] = firstExpectedTarget;
        System.arraycopy(additionalExpectedTargets, 0, expectedTargets, 1, additionalExpectedTargets.length);
        return expectedTargets;
    }

    @UriEndpoint(
            firstVersion = "3.0.0",
            scheme = "rich",
            extendsScheme = "base",
            syntax = "rich:account:operation",
            alternativeSyntax = "rich:account/operation",
            consumerPrefix = "consumer.",
            title = "Rich endpoint",
            label = "testing,core",
            producerOnly = true,
            lenientProperties = true,
            generateConfigurer = false)
    @UriParams(prefix = "advanced.")
    @Metadata(
            label = "component",
            defaultValue = "enabled",
            required = true,
            title = "Component metadata",
            description = "Metadata attached to the endpoint type")
    private static final class RichEndpoint {
        @UriPath(
                name = "account",
                defaultValue = "primary",
                defaultValueNote = "Uses the primary account when omitted",
                description = "Account identifier used in the URI path",
                enums = "primary,secondary",
                label = "path,required",
                javaType = "java.lang.String")
        private String account;

        @UriParam(
                name = "flag",
                defaultValue = "fast",
                defaultValueNote = "Multiple flags may be supplied",
                description = "Runtime flags applied to the endpoint",
                enums = "fast,safe,verbose",
                label = "producer,advanced",
                javaType = "java.util.List<java.lang.String>",
                multiValue = true,
                prefix = "flag.",
                optionalPrefix = "optional.")
        private String flags;

        @Metadata(
                label = "field",
                defaultValue = "n/a",
                required = false,
                title = "Description",
                description = "Optional endpoint description")
        private String description;

        @Metadata(
                label = "operation",
                defaultValue = "rich:primary:status",
                required = true,
                title = "URI builder",
                description = "Builds the endpoint URI")
        private String buildUri() {
            return "rich:" + account + ':' + flags;
        }
    }

    @UriEndpoint(scheme = "minimal", syntax = "minimal:path", title = "Minimal endpoint")
    @UriParams
    @Metadata
    private static final class MinimalEndpoint {
        @UriPath
        private String path;

        @UriParam
        private String option;
    }

    @UriEndpoint(scheme = "consume", syntax = "consume:queue", title = "Consumer endpoint", consumerOnly = true)
    private static final class ConsumerEndpoint {
    }

    private static final class EndpointWithNestedConfiguration {
        @UriParam(name = "security", label = "advanced,security")
        private SecurityConfiguration security;
    }

    @UriParams(prefix = "security.")
    private static final class SecurityConfiguration {
        @UriParam(
                name = "token",
                defaultValue = "anonymous",
                description = "Authentication token sent with endpoint requests",
                label = "security")
        private String token;

        @UriParam(name = "retries", defaultValue = "3", label = "advanced", javaType = "int")
        private int retries;
    }

}
