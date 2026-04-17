/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_checkerframework.checker_qual;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.checkerframework.checker.formatter.qual.ConversionCategory;
import org.checkerframework.checker.index.qual.GTENegativeOne;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.index.qual.Positive;
import org.checkerframework.checker.nullness.qual.EnsuresKeyForIf;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.checkerframework.checker.regex.qual.Regex;
import org.checkerframework.checker.regex.qual.UnknownRegex;
import org.checkerframework.common.value.qual.IntRange;
import org.checkerframework.common.value.qual.UnknownVal;
import org.checkerframework.framework.qual.ConditionalPostconditionAnnotation;
import org.checkerframework.framework.qual.DefaultFor;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.DefaultQualifierInHierarchy;
import org.checkerframework.framework.qual.InheritedAnnotation;
import org.checkerframework.framework.qual.JavaExpression;
import org.checkerframework.framework.qual.LiteralKind;
import org.checkerframework.framework.qual.QualifierArgument;
import org.checkerframework.framework.qual.QualifierForLiterals;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeKind;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.framework.qual.UpperBoundFor;
import org.junit.jupiter.api.Test;

class Checker_qualTest {
    @Test
    void coreQualifierAnnotationsExposeRuntimeContracts() throws NoSuchMethodException {
        assertTypeUseQualifierContract(NonNull.class);
        assertTypeUseQualifierContract(Nullable.class);
        assertTypeUseQualifierContract(KeyFor.class);
        assertTypeUseQualifierContract(Positive.class);
        assertTypeUseQualifierContract(NonNegative.class);
        assertTypeUseQualifierContract(Regex.class);
        assertTypeUseQualifierContract(IntRange.class);

        SubtypeOf nonNullSubtype = getRequiredAnnotation(NonNull.class, SubtypeOf.class);
        QualifierForLiterals nonNullLiterals = getRequiredAnnotation(NonNull.class, QualifierForLiterals.class);
        DefaultFor nonNullDefaultFor = getRequiredAnnotation(NonNull.class, DefaultFor.class);
        UpperBoundFor nonNullUpperBound = getRequiredAnnotation(NonNull.class, UpperBoundFor.class);

        Assertions.assertThat(nonNullSubtype.value()).containsExactly(MonotonicNonNull.class);
        Assertions.assertThat(nonNullLiterals.value()).containsExactly(LiteralKind.STRING);
        Assertions.assertThat(nonNullLiterals.stringPatterns()).isEmpty();
        Assertions.assertThat(getSingleAnnotation(NonNull.class, DefaultQualifierInHierarchy.class)).isNotNull();
        Assertions.assertThat(nonNullDefaultFor.value()).containsExactly(TypeUseLocation.EXCEPTION_PARAMETER);
        Assertions.assertThat(nonNullDefaultFor.typeKinds()).isEmpty();
        Assertions.assertThat(nonNullDefaultFor.types()).isEmpty();
        Assertions.assertThat(nonNullDefaultFor.names()).isEmpty();
        Assertions.assertThat(nonNullDefaultFor.namesExceptions()).isEmpty();
        Assertions.assertThat(nonNullUpperBound.typeKinds()).containsExactlyInAnyOrder(
                TypeKind.PACKAGE,
                TypeKind.INT,
                TypeKind.BOOLEAN,
                TypeKind.CHAR,
                TypeKind.DOUBLE,
                TypeKind.FLOAT,
                TypeKind.LONG,
                TypeKind.SHORT,
                TypeKind.BYTE);
        Assertions.assertThat(nonNullUpperBound.types()).isEmpty();

        SubtypeOf nullableSubtype = getRequiredAnnotation(Nullable.class, SubtypeOf.class);
        QualifierForLiterals nullableLiterals = getRequiredAnnotation(Nullable.class, QualifierForLiterals.class);
        DefaultFor nullableDefaultFor = getRequiredAnnotation(Nullable.class, DefaultFor.class);

        Assertions.assertThat(nullableSubtype.value()).isEmpty();
        Assertions.assertThat(nullableLiterals.value()).containsExactly(LiteralKind.NULL);
        Assertions.assertThat(nullableDefaultFor.value()).isEmpty();
        Assertions.assertThat(nullableDefaultFor.types()).containsExactly(Void.class);

        Assertions.assertThat(getRequiredAnnotation(KeyFor.class, SubtypeOf.class).value())
                .containsExactly(UnknownKeyFor.class);
        Assertions.assertThat(getRequiredAnnotation(Positive.class, SubtypeOf.class).value())
                .containsExactly(NonNegative.class);
        Assertions.assertThat(getRequiredAnnotation(NonNegative.class, SubtypeOf.class).value())
                .containsExactly(GTENegativeOne.class);
        Assertions.assertThat(getRequiredAnnotation(Regex.class, SubtypeOf.class).value())
                .containsExactly(UnknownRegex.class);
        Assertions.assertThat(getRequiredAnnotation(IntRange.class, SubtypeOf.class).value())
                .containsExactly(UnknownVal.class);

        Method keyForValueMethod = KeyFor.class.getDeclaredMethod("value");
        Method regexValueMethod = Regex.class.getDeclaredMethod("value");
        Method intRangeFromMethod = IntRange.class.getDeclaredMethod("from");
        Method intRangeToMethod = IntRange.class.getDeclaredMethod("to");

        Assertions.assertThat(getSingleAnnotation(keyForValueMethod, JavaExpression.class)).isNotNull();
        Assertions.assertThat(regexValueMethod.getDefaultValue()).isEqualTo(0);
        Assertions.assertThat(intRangeFromMethod.getDefaultValue()).isEqualTo(Long.MIN_VALUE);
        Assertions.assertThat(intRangeToMethod.getDefaultValue()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void runtimeTypeUseAnnotationsAreVisibleOnGenericSignaturesAndMembers()
            throws NoSuchFieldException, NoSuchMethodException {
        TypeVariable<Class<GenericFixture>> typeParameter = GenericFixture.class.getTypeParameters()[0];
        AnnotatedParameterizedType annotatedSuperclass =
                (AnnotatedParameterizedType) GenericFixture.class.getAnnotatedSuperclass();
        AnnotatedParameterizedType annotatedInterface =
                (AnnotatedParameterizedType) GenericFixture.class.getAnnotatedInterfaces()[0];

        Assertions.assertThat(getSingleAnnotation(typeParameter, PolyNull.class)).isNotNull();
        Assertions.assertThat(getSingleAnnotation(annotatedSuperclass, NonNull.class)).isNotNull();
        Assertions.assertThat(getSingleAnnotation(annotatedInterface, Nullable.class)).isNotNull();

        IntRange superclassRange = getRequiredAnnotation(
                annotatedSuperclass.getAnnotatedActualTypeArguments()[0],
                IntRange.class);
        KeyFor interfaceKeyFor = getRequiredAnnotation(
                annotatedInterface.getAnnotatedActualTypeArguments()[0],
                KeyFor.class);

        Assertions.assertThat(superclassRange.from()).isEqualTo(1L);
        Assertions.assertThat(superclassRange.to()).isEqualTo(4L);
        Assertions.assertThat(interfaceKeyFor.value()).containsExactly("entries");

        Field aliasesField = RuntimeFixture.class.getDeclaredField("aliases");
        Field patternField = RuntimeFixture.class.getDeclaredField("pattern");
        Field boundedCountField = RuntimeFixture.class.getDeclaredField("boundedCount");
        AnnotatedArrayType aliasesType = (AnnotatedArrayType) aliasesField.getAnnotatedType();

        Assertions.assertThat(getSingleAnnotation(aliasesType, NonNull.class)).isNotNull();
        Assertions.assertThat(getSingleAnnotation(aliasesType.getAnnotatedGenericComponentType(), Nullable.class))
                .isNotNull();
        Assertions.assertThat(getRequiredAnnotation(patternField.getAnnotatedType(), Regex.class).value()).isZero();

        IntRange boundedCountRange = getRequiredAnnotation(boundedCountField.getAnnotatedType(), IntRange.class);
        Assertions.assertThat(boundedCountRange.from()).isEqualTo(1L);
        Assertions.assertThat(boundedCountRange.to()).isEqualTo(10L);

        Method matchCount = RuntimeFixture.class.getDeclaredMethod("matchCount", int.class, List.class, List.class);
        AnnotatedParameterizedType regexListType =
                (AnnotatedParameterizedType) matchCount.getAnnotatedParameterTypes()[1];
        AnnotatedParameterizedType boundedNumbersType =
                (AnnotatedParameterizedType) matchCount.getAnnotatedParameterTypes()[2];
        AnnotatedWildcardType boundedNumberWildcard =
                (AnnotatedWildcardType) boundedNumbersType.getAnnotatedActualTypeArguments()[0];

        Assertions.assertThat(getSingleAnnotation(matchCount.getAnnotatedReturnType(), Positive.class)).isNotNull();
        Assertions.assertThat(getSingleAnnotation(matchCount.getAnnotatedParameterTypes()[0], NonNegative.class))
                .isNotNull();
        Assertions.assertThat(getRequiredAnnotation(
                regexListType.getAnnotatedActualTypeArguments()[0],
                Regex.class).value()).isEqualTo(2);

        IntRange wildcardRange = getRequiredAnnotation(
                boundedNumberWildcard.getAnnotatedUpperBounds()[0],
                IntRange.class);
        Assertions.assertThat(wildcardRange.from()).isZero();
        Assertions.assertThat(wildcardRange.to()).isEqualTo(9L);
    }

    @Test
    void repeatableConditionalAnnotationsExposeExpandedRuntimeView() throws NoSuchMethodException {
        Method containsEntry = RuntimeFixture.class.getDeclaredMethod("containsEntry", String.class);
        EnsuresNonNullIf[] ensuresNonNullIfs = containsEntry.getAnnotationsByType(EnsuresNonNullIf.class);
        EnsuresKeyForIf[] ensuresKeyForIfs = containsEntry.getAnnotationsByType(EnsuresKeyForIf.class);

        Assertions.assertThat(ensuresNonNullIfs).hasSize(2);
        Assertions.assertThat(ensuresKeyForIfs).hasSize(2);
        Assertions.assertThat(ensuresNonNullIfs).anySatisfy(annotation -> {
            Assertions.assertThat(annotation.expression()).containsExactly("pattern");
            Assertions.assertThat(annotation.result()).isTrue();
        });
        Assertions.assertThat(ensuresNonNullIfs).anySatisfy(annotation -> {
            Assertions.assertThat(annotation.expression()).containsExactly("aliases");
            Assertions.assertThat(annotation.result()).isFalse();
        });
        Assertions.assertThat(ensuresKeyForIfs).anySatisfy(annotation -> {
            Assertions.assertThat(annotation.expression()).containsExactly("#1");
            Assertions.assertThat(annotation.map()).containsExactly("entries");
            Assertions.assertThat(annotation.result()).isTrue();
        });
        Assertions.assertThat(ensuresKeyForIfs).anySatisfy(annotation -> {
            Assertions.assertThat(annotation.expression()).containsExactly("\"fallback\"");
            Assertions.assertThat(annotation.map()).containsExactly("entries");
            Assertions.assertThat(annotation.result()).isFalse();
        });

        ConditionalPostconditionAnnotation ensuresNonNullMeta =
                getRequiredAnnotation(EnsuresNonNullIf.class, ConditionalPostconditionAnnotation.class);
        ConditionalPostconditionAnnotation ensuresKeyForMeta =
                getRequiredAnnotation(EnsuresKeyForIf.class, ConditionalPostconditionAnnotation.class);
        Repeatable ensuresNonNullRepeatable = getRequiredAnnotation(EnsuresNonNullIf.class, Repeatable.class);
        Repeatable ensuresKeyForRepeatable = getRequiredAnnotation(EnsuresKeyForIf.class, Repeatable.class);
        Method keyForMapMethod = EnsuresKeyForIf.class.getDeclaredMethod("map");

        Assertions.assertThat(ensuresNonNullMeta.qualifier()).isSameAs(NonNull.class);
        Assertions.assertThat(ensuresKeyForMeta.qualifier()).isSameAs(KeyFor.class);
        Assertions.assertThat(getSingleAnnotation(EnsuresNonNullIf.class, InheritedAnnotation.class)).isNotNull();
        Assertions.assertThat(getSingleAnnotation(EnsuresKeyForIf.class, InheritedAnnotation.class)).isNotNull();
        Assertions.assertThat(ensuresNonNullRepeatable.value()).isSameAs(EnsuresNonNullIf.List.class);
        Assertions.assertThat(ensuresKeyForRepeatable.value()).isSameAs(EnsuresKeyForIf.List.class);
        Assertions.assertThat(getSingleAnnotation(keyForMapMethod, JavaExpression.class)).isNotNull();
        Assertions.assertThat(getRequiredAnnotation(keyForMapMethod, QualifierArgument.class).value())
                .isEqualTo("value");
    }

    @Test
    void frameworkAnnotationsAndEnumUtilitiesExposeDefaults() throws NoSuchMethodException {
        Retention defaultQualifierRetention = getRequiredAnnotation(DefaultQualifier.class, Retention.class);
        Target defaultQualifierTarget = getRequiredAnnotation(DefaultQualifier.class, Target.class);
        Repeatable defaultQualifierRepeatable = getRequiredAnnotation(DefaultQualifier.class, Repeatable.class);

        Assertions.assertThat(getSingleAnnotation(DefaultQualifier.class, Documented.class)).isNotNull();
        Assertions.assertThat(defaultQualifierRetention.value()).isEqualTo(RetentionPolicy.SOURCE);
        Assertions.assertThat(defaultQualifierTarget.value()).containsExactlyInAnyOrder(
                ElementType.PACKAGE,
                ElementType.TYPE,
                ElementType.CONSTRUCTOR,
                ElementType.METHOD,
                ElementType.FIELD,
                ElementType.LOCAL_VARIABLE,
                ElementType.PARAMETER);
        Assertions.assertThat(defaultQualifierRepeatable.value()).isSameAs(DefaultQualifier.List.class);
        Assertions.assertThat((TypeUseLocation[]) DefaultQualifier.class.getDeclaredMethod("locations").getDefaultValue())
                .containsExactly(TypeUseLocation.ALL);
        Assertions.assertThat(DefaultQualifier.List.class.getDeclaredMethod("value").getReturnType())
                .isEqualTo(DefaultQualifier[].class);

        Assertions.assertThat((TypeUseLocation[]) DefaultFor.class.getDeclaredMethod("value").getDefaultValue())
                .isEmpty();
        Assertions.assertThat((TypeKind[]) DefaultFor.class.getDeclaredMethod("typeKinds").getDefaultValue())
                .isEmpty();
        Assertions.assertThat((Class<?>[]) DefaultFor.class.getDeclaredMethod("types").getDefaultValue())
                .isEmpty();
        Assertions.assertThat((String[]) DefaultFor.class.getDeclaredMethod("names").getDefaultValue())
                .isEmpty();
        Assertions.assertThat((String[]) DefaultFor.class.getDeclaredMethod("namesExceptions").getDefaultValue())
                .isEmpty();
        Assertions.assertThat((TypeKind[]) UpperBoundFor.class.getDeclaredMethod("typeKinds").getDefaultValue())
                .isEmpty();
        Assertions.assertThat((Class<?>[]) UpperBoundFor.class.getDeclaredMethod("types").getDefaultValue())
                .isEmpty();

        Assertions.assertThat(LiteralKind.valueOf("STRING")).isSameAs(LiteralKind.STRING);
        Assertions.assertThat(LiteralKind.allLiteralKinds()).containsExactly(
                LiteralKind.NULL,
                LiteralKind.INT,
                LiteralKind.LONG,
                LiteralKind.FLOAT,
                LiteralKind.DOUBLE,
                LiteralKind.BOOLEAN,
                LiteralKind.CHAR,
                LiteralKind.STRING);
        Assertions.assertThat(LiteralKind.primitiveLiteralKinds()).containsExactly(
                LiteralKind.INT,
                LiteralKind.LONG,
                LiteralKind.FLOAT,
                LiteralKind.DOUBLE,
                LiteralKind.BOOLEAN,
                LiteralKind.CHAR);
        Assertions.assertThat(TypeUseLocation.valueOf("PARAMETER")).isSameAs(TypeUseLocation.PARAMETER);
        Assertions.assertThat(TypeUseLocation.values()).contains(
                TypeUseLocation.EXCEPTION_PARAMETER,
                TypeUseLocation.RETURN,
                TypeUseLocation.ALL);
        Assertions.assertThat(TypeKind.valueOf("PACKAGE")).isSameAs(TypeKind.PACKAGE);
        Assertions.assertThat(TypeKind.values()).contains(
                TypeKind.DECLARED,
                TypeKind.INTERSECTION,
                TypeKind.UNION);
    }

    @Test
    void formatterConversionCategoriesResolveFormatterSemantics() {
        Assertions.assertThat(ConversionCategory.fromConversionChar('s')).isSameAs(ConversionCategory.GENERAL);
        Assertions.assertThat(ConversionCategory.fromConversionChar('d')).isSameAs(ConversionCategory.INT);
        Assertions.assertThat(ConversionCategory.fromConversionChar('f')).isSameAs(ConversionCategory.FLOAT);
        Assertions.assertThat(ConversionCategory.fromConversionChar('T')).isSameAs(ConversionCategory.TIME);
        Assertions.assertThat(ConversionCategory.intersect(ConversionCategory.CHAR, ConversionCategory.INT))
                .isSameAs(ConversionCategory.CHAR_AND_INT);
        Assertions.assertThat(ConversionCategory.intersect(ConversionCategory.INT, ConversionCategory.TIME))
                .isSameAs(ConversionCategory.INT_AND_TIME);
        Assertions.assertThat(ConversionCategory.union(
                ConversionCategory.CHAR_AND_INT,
                ConversionCategory.INT_AND_TIME))
                .isSameAs(ConversionCategory.INT);
        Assertions.assertThat(ConversionCategory.union(ConversionCategory.INT, ConversionCategory.FLOAT))
                .isSameAs(ConversionCategory.GENERAL);
        Assertions.assertThat(ConversionCategory.isSubsetOf(
                ConversionCategory.INT_AND_TIME,
                ConversionCategory.INT))
                .isTrue();
        Assertions.assertThat(ConversionCategory.isSubsetOf(ConversionCategory.CHAR, ConversionCategory.INT))
                .isFalse();
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> ConversionCategory.fromConversionChar('z'));
    }

    @Test
    void formatterConversionCategoriesAcceptMatchingJavaTypes() {
        Assertions.assertThat(ConversionCategory.GENERAL.isAssignableFrom(Object.class)).isTrue();
        Assertions.assertThat(ConversionCategory.INT.isAssignableFrom(BigInteger.class)).isTrue();
        Assertions.assertThat(ConversionCategory.FLOAT.isAssignableFrom(BigDecimal.class)).isTrue();
        Assertions.assertThat(ConversionCategory.TIME.isAssignableFrom(Date.class)).isTrue();
        Assertions.assertThat(ConversionCategory.CHAR.isAssignableFrom(Long.class)).isFalse();
        Assertions.assertThat(ConversionCategory.NULL.isAssignableFrom(String.class)).isFalse();
        Assertions.assertThat(ConversionCategory.NULL.isAssignableFrom(Void.TYPE)).isTrue();
        Assertions.assertThat(ConversionCategory.INT.toString())
                .contains("INT conversion category")
                .contains("Long")
                .contains("BigInteger");
    }

    @Test
    void annotatedMethodsRemainUsableInRegularJavaCode() {
        RuntimeFixture runtimeFixture = new RuntimeFixture();

        Assertions.assertThat(runtimeFixture.containsEntry("alpha")).isTrue();
        Assertions.assertThat(runtimeFixture.containsEntry("missing")).isFalse();
        Assertions.assertThat(runtimeFixture.matchCount(
                1,
                List.of("[a-z]+", "[0-9]+"),
                List.<Number>of(1, 2L)))
                .isEqualTo(5);
    }

    private static void assertTypeUseQualifierContract(Class<? extends Annotation> annotationType) {
        Retention retention = getRequiredAnnotation(annotationType, Retention.class);
        Target target = getRequiredAnnotation(annotationType, Target.class);

        Assertions.assertThat(getSingleAnnotation(annotationType, Documented.class)).isNotNull();
        Assertions.assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        Assertions.assertThat(target.value()).containsExactlyInAnyOrder(ElementType.TYPE_USE, ElementType.TYPE_PARAMETER);
    }

    private static <T extends Annotation> T getRequiredAnnotation(
            AnnotatedElement annotatedElement,
            Class<T> annotationType) {
        T annotation = getSingleAnnotation(annotatedElement, annotationType);
        Assertions.assertThat(annotation).isNotNull();
        return annotation;
    }

    private static <T extends Annotation> T getSingleAnnotation(
            AnnotatedElement annotatedElement,
            Class<T> annotationType) {
        T[] annotations = annotatedElement.getAnnotationsByType(annotationType);
        return annotations.length == 0 ? null : annotations[0];
    }

    private static class Base<T> {
    }

    private interface Marker<T> {
    }

    private static final class GenericFixture<@PolyNull T>
            extends @NonNull Base<@IntRange(from = 1, to = 4) Long>
            implements @Nullable Marker<@KeyFor("entries") String> {
    }

    private static final class RuntimeFixture {
        private final Map<String, String> entries = Map.of("alpha", "value");
        private @Regex String pattern = "[a-z]+";
        private @IntRange(from = 1, to = 10) long boundedCount = 4L;
        private @Nullable String @NonNull [] aliases = {"alpha", null};

        @EnsuresNonNullIf(expression = "pattern", result = true)
        @EnsuresNonNullIf(expression = "aliases", result = false)
        @EnsuresKeyForIf(expression = "#1", map = "entries", result = true)
        @EnsuresKeyForIf(expression = "\"fallback\"", map = "entries", result = false)
        private boolean containsEntry(@KeyFor("entries") String key) {
            return this.entries.containsKey(key);
        }

        private @Positive int matchCount(
                @NonNegative int offset,
                List<@Regex(2) String> regexes,
                List<? extends @IntRange(from = 0, to = 9) Number> boundedNumbers
        ) {
            return offset + regexes.size() + boundedNumbers.size();
        }
    }
}
