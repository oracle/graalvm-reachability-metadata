/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jspecify.jspecify;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JspecifyTest {
    @Test
    void typeUseAnnotationsAreRetainedOnFieldsMethodsAndConstructors() throws Exception {
        Field requiredNameField = NullMarkedApi.class.getDeclaredField("requiredName");
        Field aliasesField = NullMarkedApi.class.getDeclaredField("aliases");
        Method findAliasMethod = NullMarkedApi.class.getDeclaredMethod("findAlias", boolean.class);
        Method namesMethod = NullMarkedApi.class.getDeclaredMethod("names");
        Method acceptAliasMethod = NullMarkedApi.class.getDeclaredMethod("acceptAlias", String.class, List.class);
        Constructor<NullMarkedApi> constructor = NullMarkedApi.class.getDeclaredConstructor(String.class, List.class);

        assertThat(annotationTypes(requiredNameField.getAnnotatedType())).containsExactly(NonNull.class);
        assertThat(annotationTypes(singleTypeArgument(aliasesField.getAnnotatedType()))).containsExactly(Nullable.class);
        assertThat(annotationTypes(findAliasMethod.getAnnotatedReturnType())).containsExactly(Nullable.class);
        assertThat(annotationTypes(singleTypeArgument(namesMethod.getAnnotatedReturnType()))).containsExactly(NonNull.class);
        assertThat(annotationTypes(acceptAliasMethod.getAnnotatedParameterTypes()[0])).containsExactly(Nullable.class);
        assertThat(annotationTypes(singleTypeArgument(acceptAliasMethod.getAnnotatedParameterTypes()[1]))).containsExactly(NonNull.class);
        assertThat(annotationTypes(constructor.getAnnotatedParameterTypes()[0])).containsExactly(NonNull.class);
        assertThat(annotationTypes(singleTypeArgument(constructor.getAnnotatedParameterTypes()[1]))).containsExactly(Nullable.class);
    }

    @Test
    void declarationAnnotationsAreRetainedOnTypesMethodsAndConstructors() throws Exception {
        Method nullMarkedMethod = NullMarkedApi.class.getDeclaredMethod("nullMarkedOperation");
        Method nullUnmarkedMethod = NullUnmarkedApi.class.getDeclaredMethod("nullUnmarkedOperation");
        Constructor<NullMarkedApi> nullMarkedConstructor = NullMarkedApi.class.getDeclaredConstructor();
        Constructor<NullUnmarkedApi> nullUnmarkedConstructor = NullUnmarkedApi.class.getDeclaredConstructor();

        assertThat(annotationTypes(NullMarkedApi.class)).containsExactly(NullMarked.class);
        assertThat(annotationTypes(nullMarkedMethod)).containsExactly(NullMarked.class);
        assertThat(annotationTypes(nullMarkedConstructor)).containsExactly(NullMarked.class);

        assertThat(annotationTypes(NullUnmarkedApi.class)).containsExactly(NullUnmarked.class);
        assertThat(annotationTypes(nullUnmarkedMethod)).containsExactly(NullUnmarked.class);
        assertThat(annotationTypes(nullUnmarkedConstructor)).containsExactly(NullUnmarked.class);
    }

    @Test
    void runtimeAnnotationsExposeStandardAnnotationContract() throws Exception {
        Nullable nullable = NullMarkedApi.class.getDeclaredMethod("findAlias", boolean.class)
                .getAnnotatedReturnType()
                .getAnnotation(Nullable.class);
        NonNull nonNull = NullMarkedApi.class.getDeclaredField("requiredName")
                .getAnnotatedType()
                .getAnnotation(NonNull.class);
        NullMarked nullMarked = NullMarkedApi.class.getAnnotation(NullMarked.class);

        assertThat(nullable).isNotNull();
        assertThat(nullable.annotationType()).isEqualTo(Nullable.class);
        assertThat(nullable).isEqualTo(NullMarkedApi.class.getDeclaredMethod("findAlias", boolean.class)
                .getAnnotatedReturnType()
                .getAnnotation(Nullable.class));
        assertThat(nullable.hashCode()).isEqualTo(0);
        assertThat(nullable.toString()).contains(Nullable.class.getName());

        assertThat(nonNull).isNotNull();
        assertThat(nonNull.annotationType()).isEqualTo(NonNull.class);
        assertThat(nonNull.hashCode()).isEqualTo(0);
        assertThat(nonNull.toString()).contains(NonNull.class.getName());

        assertThat(nullMarked).isNotNull();
        assertThat(nullMarked.annotationType()).isEqualTo(NullMarked.class);
        assertThat(nullMarked.hashCode()).isEqualTo(0);
        assertThat(nullMarked.toString()).contains(NullMarked.class.getName());
    }

    @Test
    void annotationTypesDeclareTheirDocumentedTargetAndRetentionContract() {
        assertThat(Nullable.class).hasAnnotation(Documented.class);
        assertThat(targetTypes(Nullable.class)).containsExactly(ElementType.TYPE_USE);
        assertThat(retentionPolicy(Nullable.class)).isEqualTo(RetentionPolicy.RUNTIME);

        assertThat(NonNull.class).hasAnnotation(Documented.class);
        assertThat(targetTypes(NonNull.class)).containsExactly(ElementType.TYPE_USE);
        assertThat(retentionPolicy(NonNull.class)).isEqualTo(RetentionPolicy.RUNTIME);

        assertThat(NullMarked.class).hasAnnotation(Documented.class);
        assertThat(targetTypes(NullMarked.class)).containsExactly(
                ElementType.MODULE,
                ElementType.PACKAGE,
                ElementType.TYPE,
                ElementType.METHOD,
                ElementType.CONSTRUCTOR);
        assertThat(retentionPolicy(NullMarked.class)).isEqualTo(RetentionPolicy.RUNTIME);

        assertThat(NullUnmarked.class).hasAnnotation(Documented.class);
        assertThat(targetTypes(NullUnmarked.class)).containsExactly(
                ElementType.PACKAGE,
                ElementType.TYPE,
                ElementType.METHOD,
                ElementType.CONSTRUCTOR);
        assertThat(retentionPolicy(NullUnmarked.class)).isEqualTo(RetentionPolicy.RUNTIME);
    }

    private static List<Class<? extends Annotation>> annotationTypes(AnnotatedElement annotatedElement) {
        return List.of(annotatedElement.getAnnotations()).stream()
                .map(Annotation::annotationType)
                .toList();
    }

    private static List<Class<? extends Annotation>> annotationTypes(AnnotatedType annotatedType) {
        return List.of(annotatedType.getAnnotations()).stream()
                .map(Annotation::annotationType)
                .toList();
    }

    private static List<ElementType> targetTypes(Class<? extends Annotation> annotationType) {
        return List.of(annotationType.getAnnotation(Target.class).value());
    }

    private static RetentionPolicy retentionPolicy(Class<? extends Annotation> annotationType) {
        return annotationType.getAnnotation(Retention.class).value();
    }

    private static AnnotatedType singleTypeArgument(AnnotatedType annotatedType) {
        return ((AnnotatedParameterizedType) annotatedType).getAnnotatedActualTypeArguments()[0];
    }

    @NullMarked
    static final class NullMarkedApi {
        private final @NonNull String requiredName;
        private final List<@Nullable String> aliases;

        @NullMarked
        NullMarkedApi() {
            this("primary", List.of("alias"));
        }

        NullMarkedApi(@NonNull String requiredName, List<@Nullable String> aliases) {
            this.requiredName = requiredName;
            this.aliases = aliases;
        }

        @Nullable
        String findAlias(boolean present) {
            return present ? aliases.get(0) : null;
        }

        List<@NonNull String> names() {
            return List.of(requiredName);
        }

        void acceptAlias(@Nullable String alias, List<@NonNull String> candidates) {
            assertThat(alias).isNull();
            assertThat(candidates).containsExactly(requiredName);
        }

        @NullMarked
        void nullMarkedOperation() {
            acceptAlias(null, List.of(requiredName));
        }
    }

    @NullUnmarked
    static final class NullUnmarkedApi {
        @NullUnmarked
        NullUnmarkedApi() {
        }

        @NullUnmarked
        void nullUnmarkedOperation() {
        }
    }
}
