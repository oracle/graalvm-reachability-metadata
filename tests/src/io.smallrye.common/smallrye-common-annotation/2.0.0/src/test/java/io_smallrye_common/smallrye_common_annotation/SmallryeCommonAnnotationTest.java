/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_annotation;

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

import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.common.annotation.Experimental;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.Test;

public class SmallryeCommonAnnotationTest {
    @Test
    void identifierLiteralsBehaveLikeCdiQualifierInstances() {
        Identifier first = Identifier.Literal.of("orders");
        Identifier sameValue = Identifier.Literal.of("orders");
        Identifier differentValue = Identifier.Literal.of("payments");

        assertThat(first).isInstanceOf(Annotation.class);
        assertThat(first.value()).isEqualTo("orders");
        assertThat(first.annotationType()).isEqualTo(Identifier.class);
        assertThat(first).isEqualTo(sameValue);
        assertThat(first.hashCode()).isEqualTo(sameValue.hashCode());
        assertThat(first).isNotEqualTo(differentValue);
        assertThat(first.toString()).contains("Identifier", "orders");
    }

    @Test
    void identifierLiteralMatchesRuntimeIdentifierAnnotations() {
        Identifier typeIdentifier = requireAnnotation(IdentifiedComponent.class, Identifier.class);
        Field field = declaredField(IdentifiedComponent.class, "dependency");
        Method producer = declaredMethod(IdentifiedComponent.class, "produce", String.class);
        Parameter input = producer.getParameters()[0];

        assertThat(typeIdentifier.value()).isEqualTo("component");
        assertThat(requireAnnotation(field, Identifier.class).value()).isEqualTo("field");
        assertThat(requireAnnotation(producer, Identifier.class).value()).isEqualTo("producer");
        assertThat(requireAnnotation(input, Identifier.class).value()).isEqualTo("input");
        assertThat(Identifier.Literal.of("component")).isEqualTo(typeIdentifier);
        assertThat(typeIdentifier).isEqualTo(Identifier.Literal.of("component"));
    }

    @Test
    void runtimeBehaviorAnnotationsAreAvailableOnTypesAndMethods() {
        assertThat(BlockingService.class.isAnnotationPresent(Blocking.class)).isTrue();
        assertThat(NonBlockingService.class.isAnnotationPresent(NonBlocking.class)).isTrue();
        assertThat(VirtualThreadService.class.isAnnotationPresent(RunOnVirtualThread.class)).isTrue();
        assertThat(VirtualThreadService.class.isAnnotationPresent(Blocking.class)).isTrue();

        Method blockingOperation = declaredMethod(BlockingService.class, "blockingOperation");
        Method nonBlockingOperation = declaredMethod(NonBlockingService.class, "nonBlockingOperation");
        Method virtualThreadOperation = declaredMethod(VirtualThreadService.class, "virtualThreadOperation");

        assertThat(blockingOperation.isAnnotationPresent(Blocking.class)).isTrue();
        assertThat(blockingOperation.isAnnotationPresent(CheckReturnValue.class)).isTrue();
        assertThat(requireAnnotation(blockingOperation, Experimental.class).value())
                .isEqualTo("operation contract may change");
        assertThat(nonBlockingOperation.isAnnotationPresent(NonBlocking.class)).isTrue();
        assertThat(virtualThreadOperation.isAnnotationPresent(RunOnVirtualThread.class)).isTrue();
        assertThat(virtualThreadOperation.isAnnotationPresent(Blocking.class)).isTrue();
    }

    @Test
    void metaAnnotationsDescribeSmallRyeAnnotationContracts() {
        assertRetentionAndTarget(Blocking.class, ElementType.METHOD, ElementType.TYPE);
        assertRetentionAndTarget(NonBlocking.class, ElementType.METHOD, ElementType.TYPE);
        assertRetentionAndTarget(RunOnVirtualThread.class, ElementType.METHOD, ElementType.TYPE);
        assertRetentionAndTarget(CheckReturnValue.class, ElementType.METHOD);
        assertRetentionAndTarget(Experimental.class, ElementType.METHOD, ElementType.TYPE, ElementType.FIELD,
                ElementType.PACKAGE);
        assertRetentionAndTarget(Identifier.class, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD,
                ElementType.TYPE);

        assertThat(CheckReturnValue.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(Experimental.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(Experimental.class.isAnnotationPresent(Inherited.class)).isTrue();
        assertThat(Identifier.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(Identifier.class.isAnnotationPresent(Qualifier.class)).isTrue();
        assertThat(requireAnnotation(RunOnVirtualThread.class, Experimental.class).value())
                .isEqualTo("This is an experimental feature still at the alpha stage");
    }

    @Test
    void experimentalIsInheritedButThreadingMarkersAreNot() {
        assertThat(requireAnnotation(ExperimentalSubclass.class, Experimental.class).value())
                .isEqualTo("base type contract may change");
        assertThat(BlockingSubclass.class.isAnnotationPresent(Blocking.class)).isFalse();
        assertThat(NonBlockingSubclass.class.isAnnotationPresent(NonBlocking.class)).isFalse();
    }

    private static <A extends Annotation> A requireAnnotation(AnnotatedElement element, Class<A> annotationType) {
        A annotation = element.getAnnotation(annotationType);
        assertThat(annotation).as("%s annotation on %s", annotationType.getSimpleName(), element).isNotNull();
        return annotation;
    }

    private static Method declaredMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Expected method to exist: " + type.getName() + "." + name, e);
        }
    }

    private static Field declaredField(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Expected field to exist: " + type.getName() + "." + name, e);
        }
    }

    @Identifier("component")
    static final class IdentifiedComponent {
        @Identifier("field")
        String dependency;

        @Identifier("producer")
        String produce(@Identifier("input") String input) {
            return input;
        }
    }

    @Blocking
    static class BlockingService {
        @Blocking
        @CheckReturnValue
        @Experimental("operation contract may change")
        String blockingOperation() {
            return "blocked";
        }
    }

    @NonBlocking
    static class NonBlockingService {
        @NonBlocking
        String nonBlockingOperation() {
            return "ready";
        }
    }

    @Blocking
    @RunOnVirtualThread
    static class VirtualThreadService {
        @Blocking
        @RunOnVirtualThread
        String virtualThreadOperation() {
            return "virtual";
        }
    }

    @Experimental("base type contract may change")
    static class ExperimentalBase {
    }

    static final class ExperimentalSubclass extends ExperimentalBase {
    }

    @Blocking
    static class BlockingBase {
    }

    static final class BlockingSubclass extends BlockingBase {
    }

    @NonBlocking
    static class NonBlockingBase {
    }

    static final class NonBlockingSubclass extends NonBlockingBase {
    }

    private static void assertRetentionAndTarget(Class<? extends Annotation> annotationType,
            ElementType... elementTypes) {
        assertThat(requireAnnotation(annotationType, Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(requireAnnotation(annotationType, Target.class).value()).containsExactlyInAnyOrder(elementTypes);
    }
}
