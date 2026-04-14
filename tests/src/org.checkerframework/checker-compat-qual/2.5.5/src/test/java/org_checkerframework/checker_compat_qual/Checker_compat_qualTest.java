/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_checkerframework.checker_compat_qual;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.List;

import org.assertj.core.api.Assertions;
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

class Checker_compat_qualTest {
    @Test
    void declarationAnnotationsAreVisibleAtRuntime() {
        KeyForDecl keyForDecl = getSingleAnnotation(DeclarationFixture.class, KeyForDecl.class);

        Assertions.assertThat(hasAnnotation(DeclarationFixture.class, NonNullDecl.class)).isTrue();
        Assertions.assertThat(hasAnnotation(DeclarationFixture.class, NullableDecl.class)).isTrue();
        Assertions.assertThat(hasAnnotation(DeclarationFixture.class, MonotonicNonNullDecl.class)).isTrue();
        Assertions.assertThat(hasAnnotation(DeclarationFixture.class, PolyNullDecl.class)).isTrue();
        Assertions.assertThat(keyForDecl).isNotNull();
        Assertions.assertThat(keyForDecl.value()).containsExactly("entries");
    }

    @Test
    void typeUseAnnotationsAreVisibleOnGenericSignatures() {
        TypeVariable<Class<TypeUseFixture>> typeParameter = TypeUseFixture.class.getTypeParameters()[0];
        AnnotatedType annotatedSuperclass = TypeUseFixture.class.getAnnotatedSuperclass();
        AnnotatedType annotatedInterface = TypeUseFixture.class.getAnnotatedInterfaces()[0];

        Assertions.assertThat(getSingleAnnotation(typeParameter, PolyNullType.class)).isNotNull();
        Assertions.assertThat(getSingleAnnotation(annotatedSuperclass, NonNullType.class)).isNotNull();
        Assertions.assertThat(getSingleAnnotation(annotatedInterface, NullableType.class)).isNotNull();

        AnnotatedParameterizedType parameterizedSuperclass = (AnnotatedParameterizedType) annotatedSuperclass;
        AnnotatedParameterizedType parameterizedInterface = (AnnotatedParameterizedType) annotatedInterface;

        Assertions.assertThat(
                getSingleAnnotation(
                        parameterizedSuperclass.getAnnotatedActualTypeArguments()[0],
                        MonotonicNonNullType.class)
        ).isNotNull();

        KeyForType keyForType = getSingleAnnotation(
                parameterizedInterface.getAnnotatedActualTypeArguments()[0],
                KeyForType.class);
        Assertions.assertThat(keyForType).isNotNull();
        Assertions.assertThat(keyForType.value()).containsExactly("entries");
    }

    @Test
    void typeUseAnnotationsAreVisibleOnArraysAndWildcardBounds() throws NoSuchFieldException, NoSuchMethodException {
        Field field = MemberFixture.class.getDeclaredField("entries");
        AnnotatedArrayType fieldType = (AnnotatedArrayType) field.getAnnotatedType();

        Assertions.assertThat(getSingleAnnotation(fieldType, NonNullType.class)).isNotNull();
        Assertions.assertThat(getSingleAnnotation(fieldType.getAnnotatedGenericComponentType(), NullableType.class))
                .isNotNull();

        Method method = MemberFixture.class.getDeclaredMethod("transform", String[].class, List.class);
        AnnotatedArrayType returnType = (AnnotatedArrayType) method.getAnnotatedReturnType();
        AnnotatedArrayType arrayParameterType = (AnnotatedArrayType) method.getAnnotatedParameterTypes()[0];
        AnnotatedParameterizedType wildcardListType = (AnnotatedParameterizedType) method.getAnnotatedParameterTypes()[1];

        Assertions.assertThat(getSingleAnnotation(returnType, MonotonicNonNullType.class)).isNotNull();
        Assertions.assertThat(getSingleAnnotation(returnType.getAnnotatedGenericComponentType(), PolyNullType.class))
                .isNotNull();
        Assertions.assertThat(getSingleAnnotation(arrayParameterType, NullableType.class)).isNotNull();
        Assertions.assertThat(getSingleAnnotation(arrayParameterType.getAnnotatedGenericComponentType(), NonNullType.class))
                .isNotNull();

        AnnotatedWildcardType wildcardType = (AnnotatedWildcardType) wildcardListType.getAnnotatedActualTypeArguments()[0];
        KeyForType keyForType = getSingleAnnotation(wildcardType.getAnnotatedUpperBounds()[0], KeyForType.class);
        Assertions.assertThat(keyForType).isNotNull();
        Assertions.assertThat(keyForType.value()).containsExactly("entries");
    }

    @Test
    void declarationCompatibilityAnnotationsExposeDeclarationOnlyMetaAnnotations() {
        assertDeclarationAnnotationContract(NonNullDecl.class);
        assertDeclarationAnnotationContract(NullableDecl.class);
        assertDeclarationAnnotationContract(MonotonicNonNullDecl.class);
        assertDeclarationAnnotationContract(PolyNullDecl.class);
        assertDeclarationAnnotationContract(KeyForDecl.class);
    }

    @Test
    void typeCompatibilityAnnotationsExposeTypeUseMetaAnnotations() {
        assertTypeUseAnnotationContract(NonNullType.class);
        assertTypeUseAnnotationContract(NullableType.class);
        assertTypeUseAnnotationContract(MonotonicNonNullType.class);
        assertTypeUseAnnotationContract(PolyNullType.class);
        assertTypeUseAnnotationContract(KeyForType.class);
    }

    private static void assertDeclarationAnnotationContract(Class<? extends Annotation> annotationType) {
        Retention retention = getSingleAnnotation(annotationType, Retention.class);

        Assertions.assertThat(hasAnnotation(annotationType, Documented.class)).isTrue();
        Assertions.assertThat(retention).isNotNull();
        Assertions.assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        Assertions.assertThat(getSingleAnnotation(annotationType, Target.class)).isNull();
    }

    private static void assertTypeUseAnnotationContract(Class<? extends Annotation> annotationType) {
        Retention retention = getSingleAnnotation(annotationType, Retention.class);
        Target target = getSingleAnnotation(annotationType, Target.class);

        Assertions.assertThat(hasAnnotation(annotationType, Documented.class)).isTrue();
        Assertions.assertThat(retention).isNotNull();
        Assertions.assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        Assertions.assertThat(target).isNotNull();
        Assertions.assertThat(target.value()).containsExactly(ElementType.TYPE_USE, ElementType.TYPE_PARAMETER);
    }

    private static <T extends Annotation> T getSingleAnnotation(
            AnnotatedElement annotatedElement,
            Class<T> annotationType) {
        T[] annotations = annotatedElement.getAnnotationsByType(annotationType);
        return annotations.length == 0 ? null : annotations[0];
    }

    private static boolean hasAnnotation(
            AnnotatedElement annotatedElement,
            Class<? extends Annotation> annotationType) {
        return annotatedElement.getAnnotationsByType(annotationType).length > 0;
    }

    @NonNullDecl
    @NullableDecl
    @MonotonicNonNullDecl
    @PolyNullDecl
    @KeyForDecl("entries")
    private static final class DeclarationFixture {
    }

    private static class Base<T> {
    }

    private interface Marker<T> {
    }

    private static final class TypeUseFixture<@PolyNullType T>
            extends @NonNullType Base<@MonotonicNonNullType String>
            implements @NullableType Marker<@KeyForType("entries") String> {
    }

    private static final class MemberFixture {
        private @NullableType String @NonNullType [] entries;

        private @PolyNullType String @MonotonicNonNullType [] transform(
                @NonNullType String @NullableType [] input,
                List<? extends @KeyForType("entries") String> keys
        ) {
            return input;
        }
    }
}
