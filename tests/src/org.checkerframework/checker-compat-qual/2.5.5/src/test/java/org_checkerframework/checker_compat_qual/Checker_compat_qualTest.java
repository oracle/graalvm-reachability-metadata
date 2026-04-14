/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_checkerframework.checker_compat_qual;

import java.lang.reflect.AnnotatedArrayType;
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
        KeyForDecl keyForDecl = DeclarationFixture.class.getAnnotation(KeyForDecl.class);

        Assertions.assertThat(DeclarationFixture.class.isAnnotationPresent(NonNullDecl.class)).isTrue();
        Assertions.assertThat(DeclarationFixture.class.isAnnotationPresent(NullableDecl.class)).isTrue();
        Assertions.assertThat(DeclarationFixture.class.isAnnotationPresent(MonotonicNonNullDecl.class)).isTrue();
        Assertions.assertThat(DeclarationFixture.class.isAnnotationPresent(PolyNullDecl.class)).isTrue();
        Assertions.assertThat(keyForDecl).isNotNull();
        Assertions.assertThat(keyForDecl.value()).containsExactly("entries");
    }

    @Test
    void typeUseAnnotationsAreVisibleOnGenericSignatures() {
        TypeVariable<Class<TypeUseFixture>> typeParameter = TypeUseFixture.class.getTypeParameters()[0];
        AnnotatedType annotatedSuperclass = TypeUseFixture.class.getAnnotatedSuperclass();
        AnnotatedType annotatedInterface = TypeUseFixture.class.getAnnotatedInterfaces()[0];

        Assertions.assertThat(typeParameter.getAnnotation(PolyNullType.class)).isNotNull();
        Assertions.assertThat(annotatedSuperclass.getAnnotation(NonNullType.class)).isNotNull();
        Assertions.assertThat(annotatedInterface.getAnnotation(NullableType.class)).isNotNull();

        AnnotatedParameterizedType parameterizedSuperclass = (AnnotatedParameterizedType) annotatedSuperclass;
        AnnotatedParameterizedType parameterizedInterface = (AnnotatedParameterizedType) annotatedInterface;

        Assertions.assertThat(
                parameterizedSuperclass.getAnnotatedActualTypeArguments()[0].getAnnotation(MonotonicNonNullType.class)
        ).isNotNull();

        KeyForType keyForType = parameterizedInterface.getAnnotatedActualTypeArguments()[0].getAnnotation(KeyForType.class);
        Assertions.assertThat(keyForType).isNotNull();
        Assertions.assertThat(keyForType.value()).containsExactly("entries");
    }

    @Test
    void typeUseAnnotationsAreVisibleOnArraysAndWildcardBounds() throws NoSuchFieldException, NoSuchMethodException {
        Field field = MemberFixture.class.getDeclaredField("entries");
        AnnotatedArrayType fieldType = (AnnotatedArrayType) field.getAnnotatedType();

        Assertions.assertThat(fieldType.getAnnotation(NonNullType.class)).isNotNull();
        Assertions.assertThat(fieldType.getAnnotatedGenericComponentType().getAnnotation(NullableType.class)).isNotNull();

        Method method = MemberFixture.class.getDeclaredMethod("transform", String[].class, List.class);
        AnnotatedArrayType returnType = (AnnotatedArrayType) method.getAnnotatedReturnType();
        AnnotatedArrayType arrayParameterType = (AnnotatedArrayType) method.getAnnotatedParameterTypes()[0];
        AnnotatedParameterizedType wildcardListType = (AnnotatedParameterizedType) method.getAnnotatedParameterTypes()[1];

        Assertions.assertThat(returnType.getAnnotation(MonotonicNonNullType.class)).isNotNull();
        Assertions.assertThat(returnType.getAnnotatedGenericComponentType().getAnnotation(PolyNullType.class)).isNotNull();
        Assertions.assertThat(arrayParameterType.getAnnotation(NullableType.class)).isNotNull();
        Assertions.assertThat(arrayParameterType.getAnnotatedGenericComponentType().getAnnotation(NonNullType.class))
                .isNotNull();

        AnnotatedWildcardType wildcardType = (AnnotatedWildcardType) wildcardListType.getAnnotatedActualTypeArguments()[0];
        KeyForType keyForType = wildcardType.getAnnotatedUpperBounds()[0].getAnnotation(KeyForType.class);
        Assertions.assertThat(keyForType).isNotNull();
        Assertions.assertThat(keyForType.value()).containsExactly("entries");
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
