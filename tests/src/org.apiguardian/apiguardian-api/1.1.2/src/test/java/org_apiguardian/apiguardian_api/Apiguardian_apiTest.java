/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apiguardian.apiguardian_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apiguardian.api.API;
import org.junit.jupiter.api.Test;

public class Apiguardian_apiTest {
    @Test
    void annotationTypeDeclaresRuntimeDocumentedContractAndSupportedTargets() {
        Target target = annotation(API.class, Target.class);
        Retention retention = annotation(API.class, Retention.class);

        assertThat(API.class.isAnnotation()).isTrue();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(target.value()).containsExactlyInAnyOrder(
                ElementType.TYPE,
                ElementType.METHOD,
                ElementType.CONSTRUCTOR,
                ElementType.FIELD,
                ElementType.PACKAGE);
        assertThat(annotation(API.class, Documented.class)).isNotNull();
    }

    @Test
    void statusEnumExposesAllLifecycleStatesInDeclaredOrder() {
        assertThat(API.Status.values()).containsExactly(
                API.Status.INTERNAL,
                API.Status.DEPRECATED,
                API.Status.EXPERIMENTAL,
                API.Status.MAINTAINED,
                API.Status.STABLE);
        assertThat(API.Status.valueOf("INTERNAL")).isEqualTo(API.Status.INTERNAL);
        assertThat(API.Status.valueOf("DEPRECATED")).isEqualTo(API.Status.DEPRECATED);
        assertThat(API.Status.valueOf("EXPERIMENTAL")).isEqualTo(API.Status.EXPERIMENTAL);
        assertThat(API.Status.valueOf("MAINTAINED")).isEqualTo(API.Status.MAINTAINED);
        assertThat(API.Status.valueOf("STABLE")).isEqualTo(API.Status.STABLE);
    }

    @Test
    void statusValuesReturnsDefensiveCopy() {
        API.Status[] statuses = API.Status.values();
        statuses[0] = API.Status.STABLE;

        assertThat(API.Status.values()).containsExactly(
                API.Status.INTERNAL,
                API.Status.DEPRECATED,
                API.Status.EXPERIMENTAL,
                API.Status.MAINTAINED,
                API.Status.STABLE);
    }

    @Test
    void statusValueOfRejectsUnknownLifecycleState() {
        assertThatThrownBy(() -> API.Status.valueOf("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typeAnnotationProvidesExplicitStatusVersionAndConsumers() {
        API annotation = annotation(AnnotatedFixture.class, API.class);

        assertThat(annotation.status()).isEqualTo(API.Status.STABLE);
        assertThat(annotation.since()).isEqualTo("1.0");
        assertThat(annotation.consumers()).containsExactly("library-users", "extension-authors");
    }

    @Test
    void methodAnnotationProvidesExperimentalApiMetadata() throws NoSuchMethodException {
        Method method = AnnotatedFixture.class.getMethod("operation", String.class);
        API annotation = annotation(method, API.class);

        assertThat(annotation.status()).isEqualTo(API.Status.EXPERIMENTAL);
        assertThat(annotation.since()).isEqualTo("1.1");
        assertThat(annotation.consumers()).containsExactly("method-consumer");
    }

    @Test
    void constructorAnnotationProvidesMaintainedApiMetadata() throws NoSuchMethodException {
        Constructor<AnnotatedFixture> constructor = AnnotatedFixture.class.getConstructor(String.class);
        API annotation = annotation(constructor, API.class);

        assertThat(annotation.status()).isEqualTo(API.Status.MAINTAINED);
        assertThat(annotation.since()).isEqualTo("1.0");
        assertThat(annotation.consumers()).containsExactly("constructor-consumer");
    }

    @Test
    void fieldAnnotationProvidesInternalApiMetadata() throws NoSuchFieldException {
        Field field = AnnotatedFixture.class.getField("internalState");
        API annotation = annotation(field, API.class);

        assertThat(annotation.status()).isEqualTo(API.Status.INTERNAL);
        assertThat(annotation.since()).isEqualTo("0.9");
        assertThat(annotation.consumers()).containsExactly("maintainers");
    }

    @Test
    void annotationDefaultsUseEmptySinceAndAllConsumers() throws NoSuchMethodException {
        Method method = AnnotatedFixture.class.getMethod("deprecatedOperation");
        API annotation = annotation(method, API.class);

        assertThat(annotation.status()).isEqualTo(API.Status.DEPRECATED);
        assertThat(annotation.since()).isEmpty();
        assertThat(annotation.consumers()).containsExactly("*");
    }

    @Test
    void consumersReturnsDefensiveCopy() {
        API annotation = annotation(AnnotatedFixture.class, API.class);
        String[] consumers = annotation.consumers();
        consumers[0] = "changed-consumer";

        assertThat(annotation.consumers()).containsExactly("library-users", "extension-authors");
    }

    @Test
    void apiAnnotationCanDocumentInterfaceContracts() {
        DocumentedContract contract = new DocumentedContractImplementation("api");

        assertThat(contract.describe("guardian")).isEqualTo("api-guardian");
    }

    @Test
    void annotationInstancesHaveStandardEqualityHashCodeAndStringSemantics() throws NoSuchMethodException {
        Method firstMethod = AnnotatedFixture.class.getMethod("deprecatedOperation");
        Method secondMethod = AnotherAnnotatedFixture.class.getMethod("deprecatedOperation");
        API firstAnnotation = annotation(firstMethod, API.class);
        API secondAnnotation = annotation(secondMethod, API.class);

        assertThat(firstAnnotation)
                .isEqualTo(secondAnnotation)
                .hasSameHashCodeAs(secondAnnotation);
        assertThat(firstAnnotation.toString())
                .contains("status=DEPRECATED")
                .contains("since=")
                .contains("consumers");
    }

    // Checkstyle: allow direct annotation access
    private static <A extends Annotation> A annotation(AnnotatedElement element, Class<A> annotationType) {
        AnnotatedElement elementAnnotationAccess = element;
        return elementAnnotationAccess.getAnnotation(annotationType);
    }
    // Checkstyle: disallow direct annotation access

    @API(status = API.Status.STABLE, since = "1.0", consumers = {"library-users", "extension-authors"})
    public static final class AnnotatedFixture {
        @API(status = API.Status.INTERNAL, since = "0.9", consumers = "maintainers")
        public final String internalState;

        @API(status = API.Status.MAINTAINED, since = "1.0", consumers = "constructor-consumer")
        public AnnotatedFixture(String internalState) {
            this.internalState = internalState;
        }

        @API(status = API.Status.EXPERIMENTAL, since = "1.1", consumers = "method-consumer")
        public String operation(String suffix) {
            return internalState + suffix;
        }

        @API(status = API.Status.DEPRECATED)
        public String deprecatedOperation() {
            return internalState;
        }
    }

    @API(status = API.Status.STABLE, since = "1.0", consumers = "contract-users")
    private interface DocumentedContract {
        @API(status = API.Status.MAINTAINED, since = "1.0", consumers = "callers")
        String describe(String suffix);
    }

    private static final class DocumentedContractImplementation implements DocumentedContract {
        private final String prefix;

        private DocumentedContractImplementation(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String describe(String suffix) {
            return prefix + "-" + suffix;
        }
    }

    public static final class AnotherAnnotatedFixture {
        @API(status = API.Status.DEPRECATED)
        public String deprecatedOperation() {
            return "deprecated";
        }
    }
}
