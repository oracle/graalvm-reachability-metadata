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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UnknownFormatConversionException;

import org.assertj.core.api.Assertions;
import org.checkerframework.checker.formatter.FormatUtil;
import org.checkerframework.checker.formatter.qual.ConversionCategory;
import org.checkerframework.checker.i18nformatter.I18nFormatUtil;
import org.checkerframework.checker.i18nformatter.qual.I18nConversionCategory;
import org.checkerframework.checker.index.qual.GTENegativeOne;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.index.qual.Positive;
import org.checkerframework.checker.nullness.NullnessUtil;
import org.checkerframework.checker.nullness.Opt;
import org.checkerframework.checker.nullness.qual.EnsuresKeyForIf;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.checkerframework.checker.regex.RegexUtil;
import org.checkerframework.checker.regex.qual.Regex;
import org.checkerframework.checker.regex.qual.UnknownRegex;
import org.checkerframework.checker.signedness.SignednessUtil;
import org.checkerframework.checker.units.UnitsTools;
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
    void formatterAndRegexUtilitiesValidatePublicContracts() {
        ConversionCategory[] formatCategories =
                FormatUtil.formatParameterCategories("%1$s has %2$d items and %<d more");

        Assertions.assertThat(formatCategories).containsExactly(ConversionCategory.GENERAL, ConversionCategory.INT);
        Assertions.assertThat(FormatUtil.asFormat("%d", ConversionCategory.INT)).isEqualTo("%d");
        Assertions.assertThatThrownBy(() -> FormatUtil.asFormat("%s", ConversionCategory.INT))
                .isInstanceOf(IllegalFormatException.class);
        Assertions.assertThatThrownBy(() -> FormatUtil.tryFormatSatisfiability("%q"))
                .isInstanceOf(UnknownFormatConversionException.class);

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
        Assertions.assertThat(ConversionCategory.INT.types).contains(Long.class, BigInteger.class);
        Assertions.assertThat(ConversionCategory.INT.toString())
                .contains("INT conversion category")
                .contains("long")
                .contains("BigInteger");

        Assertions.assertThat(RegexUtil.isRegex("[a-z]+"))
                .isTrue();
        Assertions.assertThat(RegexUtil.isRegex("([0-9]+)", 1)).isTrue();
        Assertions.assertThat(RegexUtil.isRegex('x')).isTrue();
        Assertions.assertThat(RegexUtil.isRegex("(", 1)).isFalse();
        Assertions.assertThat(RegexUtil.regexError("(", 1)).contains("Unclosed group");
        Assertions.assertThat(RegexUtil.regexException("(", 1)).hasMessageContaining("Unclosed group");
        Assertions.assertThat(RegexUtil.asRegex("([a-z]+)", 1)).isEqualTo("([a-z]+)");
        Assertions.assertThatThrownBy(() -> RegexUtil.asRegex("(", 1))
                .isInstanceOf(Error.class)
                .hasCauseInstanceOf(java.util.regex.PatternSyntaxException.class);

        Assertions.assertThat(I18nConversionCategory.stringToI18nConversionCategory("date"))
                .isSameAs(I18nConversionCategory.DATE);
        Assertions.assertThat(I18nConversionCategory.stringToI18nConversionCategory("time"))
                .isSameAs(I18nConversionCategory.DATE);
        Assertions.assertThat(I18nConversionCategory.stringToI18nConversionCategory("number"))
                .isSameAs(I18nConversionCategory.NUMBER);
        Assertions.assertThat(I18nConversionCategory.stringToI18nConversionCategory("choice"))
                .isSameAs(I18nConversionCategory.NUMBER);
        Assertions.assertThat(I18nConversionCategory.intersect(
                I18nConversionCategory.DATE,
                I18nConversionCategory.NUMBER))
                .isSameAs(I18nConversionCategory.NUMBER);
        Assertions.assertThat(I18nConversionCategory.union(
                I18nConversionCategory.DATE,
                I18nConversionCategory.NUMBER))
                .isSameAs(I18nConversionCategory.DATE);
        Assertions.assertThat(I18nConversionCategory.isSubsetOf(
                I18nConversionCategory.NUMBER,
                I18nConversionCategory.DATE))
                .isTrue();
        Assertions.assertThat(I18nConversionCategory.isSubsetOf(
                I18nConversionCategory.DATE,
                I18nConversionCategory.NUMBER))
                .isFalse();
        Assertions.assertThat(I18nConversionCategory.DATE.types).contains(Date.class, Number.class);
        Assertions.assertThat(I18nConversionCategory.DATE.toString())
                .contains("DATE conversion category")
                .contains("java.util.Date")
                .contains("java.lang.Number");
        Assertions.assertThatThrownBy(() -> I18nConversionCategory.stringToI18nConversionCategory("currency"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void i18nFormatUtilParsesAndValidatesMessageFormatPatterns() {
        String messagePattern =
                "{0,choice,0#no files|1#one file|1<{0,number,integer} files} processed at {1,time,short}";
        String invalidPattern = "{0,number,#.#.#}";

        I18nConversionCategory[] parameterCategories = I18nFormatUtil.formatParameterCategories(messagePattern);

        Assertions.assertThat(parameterCategories)
                .containsExactly(I18nConversionCategory.NUMBER, I18nConversionCategory.DATE);
        Assertions.assertThat(I18nFormatUtil.isFormat(messagePattern)).isTrue();
        Assertions.assertThat(I18nFormatUtil.hasFormat(
                messagePattern,
                I18nConversionCategory.NUMBER,
                I18nConversionCategory.DATE)).isTrue();
        Assertions.assertThat(I18nFormatUtil.hasFormat(
                messagePattern,
                I18nConversionCategory.DATE,
                I18nConversionCategory.DATE)).isFalse();
        Assertions.assertThatCode(() -> I18nFormatUtil.tryFormatSatisfiability(messagePattern))
                .doesNotThrowAnyException();
        Assertions.assertThat(I18nFormatUtil.isFormat(invalidPattern)).isFalse();
        Assertions.assertThatThrownBy(() -> I18nFormatUtil.formatParameterCategories(invalidPattern))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> I18nFormatUtil.tryFormatSatisfiability(invalidPattern))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullnessAndSignednessUtilitiesSupportRegularJavaFlows() throws Exception {
        String presentValue = "value";
        String missingValue = null;
        List<String> seenValues = new ArrayList<>();
        String[] names = {"alpha", "beta"};
        String[][] matrix = {{"left"}, {"right"}};

        Assertions.assertThat(Opt.get(presentValue)).isEqualTo("value");
        Assertions.assertThatThrownBy(() -> Opt.get((String) null))
                .isInstanceOf(NoSuchElementException.class);
        Assertions.assertThat(Opt.isPresent(presentValue)).isTrue();
        Assertions.assertThat(Opt.isPresent(missingValue)).isFalse();

        Opt.ifPresent(presentValue, seenValues::add);
        Opt.ifPresent(missingValue, seenValues::add);

        Assertions.assertThat(seenValues).containsExactly("value");
        Assertions.assertThat(Opt.filter(presentValue, value -> value.startsWith("val"))).isEqualTo("value");
        Assertions.assertThat(Opt.filter(presentValue, value -> value.startsWith("no"))).isNull();
        Assertions.assertThat(Opt.map(presentValue, String::length)).isEqualTo(5);
        Assertions.assertThat(Opt.map(missingValue, String::length)).isNull();
        Assertions.assertThat(Opt.orElse(missingValue, "fallback")).isEqualTo("fallback");
        Assertions.assertThat(Opt.orElse(presentValue, "fallback")).isEqualTo("value");
        Assertions.assertThat(Opt.orElseGet(missingValue, () -> "generated")).isEqualTo("generated");
        Assertions.assertThatThrownBy(() -> Opt.orElseThrow(missingValue, () -> new IllegalStateException("missing")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("missing");

        Assertions.assertThat(NullnessUtil.castNonNull(presentValue)).isEqualTo("value");
        Assertions.assertThat(NullnessUtil.castNonNullDeep(names)).containsExactly("alpha", "beta");
        String[][] nonNullMatrix = NullnessUtil.castNonNullDeep(matrix);

        Assertions.assertThat(nonNullMatrix.length).isEqualTo(2);
        Assertions.assertThat(nonNullMatrix[0]).containsExactly("left");
        Assertions.assertThat(nonNullMatrix[1]).containsExactly("right");

        ByteBuffer byteBuffer = ByteBuffer.allocate(7);
        SignednessUtil.putUnsigned(byteBuffer, (byte) 0xC8);
        SignednessUtil.putUnsignedShort(byteBuffer, (short) 32000);
        SignednessUtil.putUnsignedInt(byteBuffer, 123456789);
        byteBuffer.flip();

        Assertions.assertThat(Byte.toUnsignedInt(SignednessUtil.getUnsigned(byteBuffer))).isEqualTo(200);
        Assertions.assertThat(Short.toUnsignedInt(SignednessUtil.getUnsignedShort(byteBuffer))).isEqualTo(32000);
        Assertions.assertThat(SignednessUtil.getUnsignedInt(byteBuffer)).isEqualTo(123456789);

        IntBuffer intBuffer = IntBuffer.allocate(3);
        SignednessUtil.putUnsigned(intBuffer, 1);
        SignednessUtil.putUnsigned(intBuffer, new int[] {2, 3}, 0, 2);
        intBuffer.flip();

        Assertions.assertThat(SignednessUtil.getUnsigned(intBuffer, 0)).isEqualTo(1);
        Assertions.assertThat(SignednessUtil.getUnsigned(intBuffer, 1)).isEqualTo(2);
        Assertions.assertThat(SignednessUtil.getUnsigned(intBuffer, 2)).isEqualTo(3);
        Assertions.assertThat(SignednessUtil.compareUnsigned((byte) -1, (byte) 1)).isGreaterThan(0);
        Assertions.assertThat(SignednessUtil.toUnsignedString((byte) -1)).isEqualTo("255");
        Assertions.assertThat(SignednessUtil.toUnsignedString((short) -1)).isEqualTo("65535");
        Assertions.assertThat(Short.toUnsignedInt(SignednessUtil.toUnsignedShort((byte) -1))).isEqualTo(255);
        Assertions.assertThat(SignednessUtil.toUnsignedLong((char) 0x00FF)).isEqualTo(255L);
        Assertions.assertThat(SignednessUtil.byteFromDouble(255.0d)).isEqualTo((byte) -1);
        Assertions.assertThat(SignednessUtil.intFromFloat(123456789.0f)).isEqualTo(123456792);
    }

    @Test
    void unitsToolsConvertsBetweenCommonUnits() {
        Assertions.assertThat(UnitsTools.toRadians(180.0d))
                .isCloseTo(Math.PI, Assertions.within(1.0e-12));
        Assertions.assertThat(UnitsTools.toDegrees(Math.PI / 2.0d))
                .isCloseTo(90.0d, Assertions.within(1.0e-12));

        Assertions.assertThat(UnitsTools.fromMilliMeterToMeter(1999)).isEqualTo(1);
        Assertions.assertThat(UnitsTools.fromMeterToMilliMeter(7)).isEqualTo(7000);
        Assertions.assertThat(UnitsTools.fromMeterToKiloMeter(2500)).isEqualTo(2);
        Assertions.assertThat(UnitsTools.fromKiloMeterToMeter(3)).isEqualTo(3000);
        Assertions.assertThat(UnitsTools.fromGramToKiloGram(2500)).isEqualTo(2);
        Assertions.assertThat(UnitsTools.fromKiloGramToGram(4)).isEqualTo(4000);

        Assertions.assertThat(UnitsTools.fromMeterPerSecondToKiloMeterPerHour(12.5d))
                .isEqualTo(45.0d);
        Assertions.assertThat(UnitsTools.fromKiloMeterPerHourToMeterPerSecond(72.0d))
                .isEqualTo(20.0d);
        Assertions.assertThat(UnitsTools.fromKelvinToCelsius(300)).isEqualTo(27);
        Assertions.assertThat(UnitsTools.fromCelsiusToKelvin(27)).isEqualTo(300);
        Assertions.assertThat(UnitsTools.fromSecondToMinute(125)).isEqualTo(2);
        Assertions.assertThat(UnitsTools.fromMinuteToSecond(3)).isEqualTo(180);
        Assertions.assertThat(UnitsTools.fromMinuteToHour(125)).isEqualTo(2);
        Assertions.assertThat(UnitsTools.fromHourToMinute(2)).isEqualTo(120);
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
