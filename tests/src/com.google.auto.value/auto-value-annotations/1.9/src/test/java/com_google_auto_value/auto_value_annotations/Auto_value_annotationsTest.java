/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value_annotations;

import com.google.auto.value.AutoAnnotation;
import com.google.auto.value.AutoBuilder;
import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.auto.value.extension.serializable.SerializableAutoValue;
import com.google.auto.value.extension.toprettystring.ToPrettyString;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Auto_value_annotationsTest {

    @Test
    void autoValueAnnotationsExposeTheirNestedContracts() throws NoSuchMethodException {
        assertThat(AutoValue.class.isAnnotation()).isTrue();
        assertThat(AutoValue.Builder.class.isAnnotation()).isTrue();
        assertThat(AutoValue.CopyAnnotations.class.isAnnotation()).isTrue();
        assertThat(AutoValue.Builder.class.getDeclaringClass()).isEqualTo(AutoValue.class);
        assertThat(AutoValue.CopyAnnotations.class.getDeclaringClass()).isEqualTo(AutoValue.class);

        assertRetention(AutoValue.class, RetentionPolicy.CLASS);
        assertRetention(AutoValue.Builder.class, RetentionPolicy.CLASS);
        assertRetention(AutoValue.CopyAnnotations.class, RetentionPolicy.CLASS);
        assertTargets(AutoValue.class, ElementType.TYPE);
        assertTargets(AutoValue.Builder.class, ElementType.TYPE);
        assertTargets(AutoValue.CopyAnnotations.class, ElementType.TYPE, ElementType.METHOD);

        Method excludeMethod = AutoValue.CopyAnnotations.class.getDeclaredMethod("exclude");
        assertThat(excludeMethod.getReturnType()).isEqualTo(Class[].class);
        assertThat((Class<?>[]) excludeMethod.getDefaultValue()).isEmpty();

        assertThat(SampleValue.class.getAnnotation(AutoValue.class)).isNull();
        assertThat(SampleValue.class.getAnnotation(AutoValue.CopyAnnotations.class)).isNull();
        assertThat(SampleValue.Builder.class.getAnnotation(AutoValue.Builder.class)).isNull();
        assertThat(method(SampleValue.class, "name").getAnnotation(AutoValue.CopyAnnotations.class)).isNull();
    }

    @Test
    void autoBuilderAndAutoOneOfExposeExpectedElements() throws NoSuchMethodException {
        assertRetention(AutoBuilder.class, RetentionPolicy.CLASS);
        assertRetention(AutoOneOf.class, RetentionPolicy.CLASS);
        assertTargets(AutoBuilder.class, ElementType.TYPE);
        assertTargets(AutoOneOf.class, ElementType.TYPE);

        Method callMethod = AutoBuilder.class.getDeclaredMethod("callMethod");
        assertThat(callMethod.getReturnType()).isEqualTo(String.class);
        assertThat(callMethod.getDefaultValue()).isEqualTo("");

        Method ofClassMethod = AutoBuilder.class.getDeclaredMethod("ofClass");
        assertThat(ofClassMethod.getReturnType()).isEqualTo(Class.class);
        assertThat(ofClassMethod.getDefaultValue()).isEqualTo(Void.class);

        Method autoOneOfValue = AutoOneOf.class.getDeclaredMethod("value");
        assertThat(autoOneOfValue.getReturnType()).isEqualTo(Class.class);
        assertThat(autoOneOfValue.getDefaultValue()).isNull();

        assertThat(SampleFactoryBuilder.class.getAnnotation(AutoBuilder.class)).isNull();
        assertThat(SampleChoice.class.getAnnotation(AutoOneOf.class)).isNull();
    }

    @Test
    void sourceRetentionAnnotationsRemainCompileTimeOnly() throws NoSuchMethodException {
        assertRetention(AutoAnnotation.class, RetentionPolicy.SOURCE);
        assertRetention(SerializableAutoValue.class, RetentionPolicy.SOURCE);
        assertTargets(AutoAnnotation.class, ElementType.METHOD);
        assertTargets(SerializableAutoValue.class, ElementType.TYPE);

        assertThat(method(AutoAnnotationSamples.class, "generatedMarker", String.class)
                .getAnnotation(AutoAnnotation.class))
                .isNull();
        assertThat(SerializableSample.class.getAnnotation(SerializableAutoValue.class)).isNull();
    }

    @Test
    void extensionAnnotationsPublishMethodLevelContracts() throws NoSuchMethodException {
        assertThat(Memoized.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(ToPrettyString.class.isAnnotationPresent(Documented.class)).isTrue();

        assertRetention(Memoized.class, RetentionPolicy.CLASS);
        assertThat(ToPrettyString.class.getAnnotation(Retention.class)).isNull();
        assertTargets(Memoized.class, ElementType.METHOD);
        assertTargets(ToPrettyString.class, ElementType.METHOD);

        assertThat(method(ExtensionSamples.class, "memoizedValue").getAnnotation(Memoized.class)).isNull();
        assertThat(method(ExtensionSamples.class, "prettyString").getAnnotation(ToPrettyString.class)).isNull();
    }

    @Test
    void annotationMembersExposeTypeSafeGenericSignatures() throws NoSuchMethodException {
        WildcardType autoBuilderWildcard = wildcardArgument(
                classReturnType(method(AutoBuilder.class, "ofClass").getGenericReturnType()));
        assertThat(autoBuilderWildcard.getUpperBounds()).containsExactly(Object.class);
        assertThat(autoBuilderWildcard.getLowerBounds()).isEmpty();

        WildcardType autoOneOfWildcard = wildcardArgument(
                classReturnType(method(AutoOneOf.class, "value").getGenericReturnType()));
        Type autoOneOfUpperBound = singleUpperBound(autoOneOfWildcard);
        assertThat(autoOneOfUpperBound).isInstanceOf(ParameterizedType.class);
        ParameterizedType enumType = (ParameterizedType) autoOneOfUpperBound;
        assertThat(enumType.getRawType()).isEqualTo(Enum.class);
        WildcardType enumArgumentWildcard = wildcardArgument(enumType);
        assertThat(enumArgumentWildcard.getUpperBounds()).containsExactly(Object.class);
        assertThat(enumArgumentWildcard.getLowerBounds()).isEmpty();

        Type excludeReturnType = method(AutoValue.CopyAnnotations.class, "exclude").getGenericReturnType();
        assertThat(excludeReturnType).isInstanceOf(GenericArrayType.class);
        GenericArrayType excludeArrayType = (GenericArrayType) excludeReturnType;
        WildcardType excludeWildcard = wildcardArgument(classReturnType(excludeArrayType.getGenericComponentType()));
        assertThat(excludeWildcard.getUpperBounds()).containsExactly(Annotation.class);
        assertThat(excludeWildcard.getLowerBounds()).isEmpty();
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return type.getDeclaredMethod(name, parameterTypes);
    }

    private static ParameterizedType classReturnType(Type type) {
        assertThat(type).isInstanceOf(ParameterizedType.class);
        ParameterizedType parameterizedType = (ParameterizedType) type;
        assertThat(parameterizedType.getRawType()).isEqualTo(Class.class);
        assertThat(parameterizedType.getActualTypeArguments()).hasSize(1);
        return parameterizedType;
    }

    private static WildcardType wildcardArgument(ParameterizedType type) {
        Type actualTypeArgument = type.getActualTypeArguments()[0];
        assertThat(actualTypeArgument).isInstanceOf(WildcardType.class);
        return (WildcardType) actualTypeArgument;
    }

    private static Type singleUpperBound(WildcardType type) {
        assertThat(type.getUpperBounds()).hasSize(1);
        return type.getUpperBounds()[0];
    }

    private static void assertRetention(Class<? extends Annotation> annotationType, RetentionPolicy expectedPolicy) {
        Retention retention = annotationType.getAnnotation(Retention.class);
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(expectedPolicy);
    }

    private static void assertTargets(Class<? extends Annotation> annotationType, ElementType... expectedTargets) {
        Target target = annotationType.getAnnotation(Target.class);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(expectedTargets);
    }

    @AutoValue
    @AutoValue.CopyAnnotations
    abstract static class SampleValue {

        @AutoValue.CopyAnnotations(exclude = Deprecated.class)
        abstract String name();

        @AutoValue.Builder
        abstract static class Builder {
            abstract Builder name(String name);

            abstract SampleValue build();
        }
    }

    @AutoBuilder(callMethod = "create", ofClass = SampleFactories.class)
    abstract static class SampleFactoryBuilder {
        abstract SampleChoice create(String value);
    }

    @AutoOneOf(SampleChoice.Kind.class)
    abstract static class SampleChoice {
        enum Kind {
            TEXT
        }

        abstract Kind kind();
    }

    static final class SampleFactories {
        private SampleFactories() {
        }

        static SampleChoice create(String value) {
            return null;
        }
    }

    @interface Marker {
        String value();
    }

    static final class AutoAnnotationSamples {
        private AutoAnnotationSamples() {
        }

        @AutoAnnotation
        static Marker generatedMarker(String value) {
            return null;
        }
    }

    @SerializableAutoValue
    @AutoValue
    abstract static class SerializableSample {
        abstract String value();
    }

    static class ExtensionSamples {
        @Memoized
        String memoizedValue() {
            return "memoized";
        }

        @ToPrettyString
        String prettyString() {
            return "ExtensionSamples{}";
        }
    }
}
