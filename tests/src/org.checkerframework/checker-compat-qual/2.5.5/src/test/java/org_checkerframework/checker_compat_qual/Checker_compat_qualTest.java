/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_checkerframework.checker_compat_qual;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.List;

import org.checkerframework.checker.nullness.compatqual.KeyForDecl;
import org.checkerframework.checker.nullness.compatqual.KeyForType;
import org.checkerframework.checker.nullness.compatqual.MonotonicNonNullDecl;
import org.checkerframework.checker.nullness.compatqual.MonotonicNonNullType;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NonNullType;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.compatqual.PolyNullDecl;
import org.checkerframework.checker.nullness.compatqual.PolyNullType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Checker_compat_qualTest {
    @Test
    void declarationAnnotationsAreRetainedOnAnnotatedElements() throws Exception {
        assertThat(annotationOn(DeclarationFixture.class, NonNullDecl.class).annotationType())
                .isEqualTo(NonNullDecl.class);
        assertThat(annotationOn(DeclarationFixture.class.getField("monotonicField"), MonotonicNonNullDecl.class)
                .annotationType()).isEqualTo(MonotonicNonNullDecl.class);
        assertThat(annotationOn(DeclarationFixture.class.getMethod("nullableMethod"), NullableDecl.class)
                .annotationType()).isEqualTo(NullableDecl.class);
        assertThat(annotationOn(DeclarationFixture.class.getMethod("acceptPolyParameter", String.class)
                .getParameters()[0], PolyNullDecl.class).annotationType()).isEqualTo(PolyNullDecl.class);

        KeyForDecl keyForDecl = annotationOn(DeclarationFixture.class.getField("declaredKey"), KeyForDecl.class);
        assertThat(keyForDecl.annotationType()).isEqualTo(KeyForDecl.class);
        assertThat(keyForDecl.value()).containsExactly("valuesByName", "fallbackValues");
    }

    @Test
    void typeUseAnnotationsAreRetainedOnFieldsMethodReturnsAndParameters() throws Exception {
        assertThat(typeAnnotationOn(TypeUseFixture.class.getField("nonNullField").getAnnotatedType(), NonNullType.class)
                .annotationType()).isEqualTo(NonNullType.class);
        assertThat(typeAnnotationOn(TypeUseFixture.class.getField("monotonicField").getAnnotatedType(), MonotonicNonNullType.class)
                .annotationType()).isEqualTo(MonotonicNonNullType.class);
        assertThat(typeAnnotationOn(TypeUseFixture.class.getMethod("nullableReturn").getAnnotatedReturnType(), NullableType.class)
                .annotationType()).isEqualTo(NullableType.class);
        assertThat(typeAnnotationOn(TypeUseFixture.class.getMethod("acceptPolyParameter", Object.class)
                .getAnnotatedParameterTypes()[0], PolyNullType.class).annotationType()).isEqualTo(PolyNullType.class);

        AnnotatedParameterizedType annotatedListType =
                (AnnotatedParameterizedType) TypeUseFixture.class.getField("typedKeys").getAnnotatedType();
        KeyForType keyForType = typeAnnotationOn(
                annotatedListType.getAnnotatedActualTypeArguments()[0], KeyForType.class);
        assertThat(keyForType.annotationType()).isEqualTo(KeyForType.class);
        assertThat(keyForType.value()).containsExactly("valuesByName");
    }

    @Test
    void typeAnnotationsSupportTypeParameterTargets() throws Exception {
        TypeVariable<Class<TypeParameterFixture>> classTypeParameter = TypeParameterFixture.class.getTypeParameters()[0];
        assertThat(annotationOn(classTypeParameter, NullableType.class).annotationType())
                .isEqualTo(NullableType.class);

        Method method = TypeParameterFixture.class.getMethod("echo", Object.class);
        TypeVariable<Method> methodTypeParameter = method.getTypeParameters()[0];
        assertThat(annotationOn(methodTypeParameter, PolyNullType.class).annotationType())
                .isEqualTo(PolyNullType.class);
        assertThat(typeAnnotationOn(method.getAnnotatedReturnType(), NonNullType.class).annotationType())
                .isEqualTo(NonNullType.class);
    }

    @Test
    void typeAnnotationsAreRetainedOnWildcardBoundsAndArrayComponents() throws Exception {
        AnnotatedParameterizedType wildcardContainerType =
                (AnnotatedParameterizedType) NestedTypeUseFixture.class.getField("boundedValues").getAnnotatedType();
        AnnotatedWildcardType wildcardType =
                (AnnotatedWildcardType) wildcardContainerType.getAnnotatedActualTypeArguments()[0];
        assertThat(typeAnnotationOn(wildcardType.getAnnotatedUpperBounds()[0], NonNullType.class).annotationType())
                .isEqualTo(NonNullType.class);

        AnnotatedArrayType annotatedArrayType =
                (AnnotatedArrayType) NestedTypeUseFixture.class.getField("annotatedArray").getAnnotatedType();
        assertThat(typeAnnotationOn(annotatedArrayType, NonNullType.class).annotationType())
                .isEqualTo(NonNullType.class);
        assertThat(typeAnnotationOn(annotatedArrayType.getAnnotatedGenericComponentType(), NullableType.class)
                .annotationType()).isEqualTo(NullableType.class);
    }

    @Test
    void typeAnnotationsAreRetainedOnMethodReceiverTypes() throws Exception {
        Method method = ReceiverTypeFixture.class.getMethod("replaceValue", String.class);
        assertThat(typeAnnotationOn(method.getAnnotatedReceiverType(), NonNullType.class).annotationType())
                .isEqualTo(NonNullType.class);
    }

    private static <A extends Annotation> A annotationOn(AnnotatedElement element, Class<A> annotationType) {
        A annotation = element.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        return annotation;
    }

    private static <A extends Annotation> A typeAnnotationOn(AnnotatedType annotatedType, Class<A> annotationType) {
        A annotation = annotatedType.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        return annotation;
    }

    @NonNullDecl
    static final class DeclarationFixture {
        @MonotonicNonNullDecl
        public String monotonicField;

        @KeyForDecl({"valuesByName", "fallbackValues"})
        public String declaredKey;

        @NullableDecl
        public String nullableMethod() {
            return null;
        }

        public void acceptPolyParameter(@PolyNullDecl String value) {
            assertThat(value).isNotNull();
        }
    }

    static final class TypeUseFixture {
        public @NonNullType String nonNullField = "value";

        public @MonotonicNonNullType String monotonicField;

        public List<@KeyForType("valuesByName") String> typedKeys = List.of("primary");

        public @NullableType String nullableReturn() {
            return null;
        }

        public void acceptPolyParameter(@PolyNullType Object value) {
            assertThat(value).isNotNull();
        }
    }

    static final class TypeParameterFixture<@NullableType T> {
        public <@PolyNullType U> @NonNullType U echo(U value) {
            assertThat(value).isNotNull();
            return value;
        }
    }

    static final class NestedTypeUseFixture {
        public List<? extends @NonNullType CharSequence> boundedValues = List.of("value");

        public @NullableType String @NonNullType [] annotatedArray = {"value"};
    }

    static final class ReceiverTypeFixture {
        private String value = "value";

        public void replaceValue(@NonNullType ReceiverTypeFixture this, String newValue) {
            this.value = newValue;
        }
    }
}
