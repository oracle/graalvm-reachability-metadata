/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_checkerframework.checker_qual;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.assertj.core.api.Assertions;
import org.checkerframework.checker.calledmethods.qual.CalledMethods;
import org.checkerframework.checker.calledmethods.qual.EnsuresCalledMethods;
import org.checkerframework.checker.formatter.qual.ConversionCategory;
import org.checkerframework.checker.formatter.qual.Format;
import org.checkerframework.checker.i18nformatter.qual.I18nConversionCategory;
import org.checkerframework.checker.i18nformatter.qual.I18nFormat;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.optional.qual.MaybePresent;
import org.checkerframework.checker.optional.qual.Present;
import org.checkerframework.checker.regex.qual.Regex;
import org.checkerframework.checker.signature.qual.BinaryName;
import org.checkerframework.checker.signature.qual.FullyQualifiedName;
import org.checkerframework.checker.signedness.qual.Unsigned;
import org.checkerframework.checker.tainting.qual.Untainted;
import org.checkerframework.checker.units.qual.Prefix;
import org.checkerframework.checker.units.qual.UnitsMultiple;
import org.checkerframework.checker.units.qual.km;
import org.checkerframework.checker.units.qual.m;
import org.checkerframework.common.returnsreceiver.qual.This;
import org.checkerframework.common.value.qual.IntRange;
import org.checkerframework.common.value.qual.MinLen;
import org.checkerframework.common.value.qual.StringVal;
import org.checkerframework.dataflow.qual.Deterministic;
import org.checkerframework.dataflow.qual.Pure;
import org.checkerframework.dataflow.qual.SideEffectFree;
import org.checkerframework.framework.qual.AnnotatedFor;
import org.checkerframework.framework.qual.ConditionalPostconditionAnnotation;
import org.checkerframework.framework.qual.LiteralKind;
import org.checkerframework.framework.qual.MonotonicQualifier;
import org.checkerframework.framework.qual.PostconditionAnnotation;
import org.checkerframework.framework.qual.PreconditionAnnotation;
import org.checkerframework.framework.qual.QualifierForLiterals;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TargetLocations;
import org.checkerframework.framework.qual.TypeKind;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.junit.jupiter.api.Test;

class Checker_qualTest {
    private static final @Format({ConversionCategory.GENERAL, ConversionCategory.INT}) String PERSON_TEMPLATE =
            "%s:%d";
    private static final @I18nFormat({I18nConversionCategory.DATE, I18nConversionCategory.NUMBER}) String REPORT_TEMPLATE =
            "{0,date,yyyy-MM-dd} -> {1,number,integer}";
    private static final @BinaryName String STRING_BINARY_NAME = "java.lang.String";
    private static final @FullyQualifiedName String ENTRY_FQ_NAME = "java.util.Map.Entry";
    private static final @StringVal({"https", "http"}) String DEFAULT_SCHEME = "https";

    @Test
    void conversionCategoryModelsFormatterConversions() {
        Assertions.assertThat(ConversionCategory.fromConversionChar('s')).isEqualTo(ConversionCategory.GENERAL);
        Assertions.assertThat(ConversionCategory.fromConversionChar('d')).isEqualTo(ConversionCategory.INT);
        Assertions.assertThat(ConversionCategory.fromConversionChar('f')).isEqualTo(ConversionCategory.FLOAT);
        Assertions.assertThat(ConversionCategory.fromConversionChar('T')).isEqualTo(ConversionCategory.TIME);

        Assertions.assertThat(ConversionCategory.intersect(ConversionCategory.CHAR, ConversionCategory.INT))
                .isEqualTo(ConversionCategory.CHAR_AND_INT);
        Assertions.assertThat(ConversionCategory.union(ConversionCategory.CHAR_AND_INT, ConversionCategory.INT_AND_TIME))
                .isEqualTo(ConversionCategory.INT);
        Assertions.assertThat(ConversionCategory.union(ConversionCategory.CHAR, ConversionCategory.FLOAT))
                .isEqualTo(ConversionCategory.GENERAL);
        Assertions.assertThat(ConversionCategory.isSubsetOf(ConversionCategory.CHAR_AND_INT, ConversionCategory.CHAR))
                .isTrue();
        Assertions.assertThat(ConversionCategory.isSubsetOf(ConversionCategory.CHAR, ConversionCategory.CHAR_AND_INT))
                .isFalse();

        Assertions.assertThat(ConversionCategory.INT.isAssignableFrom(BigInteger.class)).isTrue();
        Assertions.assertThat(ConversionCategory.FLOAT.isAssignableFrom(BigDecimal.class)).isTrue();
        Assertions.assertThat(ConversionCategory.TIME.isAssignableFrom(Date.class)).isTrue();
        Assertions.assertThat(ConversionCategory.CHAR.isAssignableFrom(char.class)).isTrue();
        Assertions.assertThat(ConversionCategory.NULL.isAssignableFrom(void.class)).isTrue();
        Assertions.assertThat(ConversionCategory.TIME.isAssignableFrom(String.class)).isFalse();

        Assertions.assertThat(ConversionCategory.TIME.toString())
                .contains("TIME conversion category")
                .contains("Calendar")
                .contains("Date");

        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> ConversionCategory.fromConversionChar('q'))
                .withMessageContaining("Bad conversion character q");
    }

    @Test
    void i18nConversionCategoryModelsMessageFormatConversions() {
        Assertions.assertThat(I18nConversionCategory.stringToI18nConversionCategory("DATE"))
                .isEqualTo(I18nConversionCategory.DATE);
        Assertions.assertThat(I18nConversionCategory.stringToI18nConversionCategory("choice"))
                .isEqualTo(I18nConversionCategory.NUMBER);

        Assertions.assertThat(I18nConversionCategory.intersect(
                I18nConversionCategory.DATE,
                I18nConversionCategory.NUMBER)).isEqualTo(I18nConversionCategory.NUMBER);
        Assertions.assertThat(I18nConversionCategory.union(
                I18nConversionCategory.DATE,
                I18nConversionCategory.NUMBER)).isEqualTo(I18nConversionCategory.DATE);
        Assertions.assertThat(I18nConversionCategory.isSubsetOf(
                I18nConversionCategory.NUMBER,
                I18nConversionCategory.DATE)).isTrue();
        Assertions.assertThat(I18nConversionCategory.isSubsetOf(
                I18nConversionCategory.DATE,
                I18nConversionCategory.NUMBER)).isFalse();

        Assertions.assertThat(I18nConversionCategory.DATE.isAssignableFrom(Date.class)).isTrue();
        Assertions.assertThat(I18nConversionCategory.DATE.isAssignableFrom(Long.class)).isTrue();
        Assertions.assertThat(I18nConversionCategory.NUMBER.isAssignableFrom(Integer.class)).isTrue();
        Assertions.assertThat(I18nConversionCategory.NUMBER.isAssignableFrom(Date.class)).isFalse();

        Assertions.assertThat(I18nConversionCategory.DATE.toString())
                .contains("DATE conversion category")
                .contains(Date.class.getCanonicalName())
                .contains(Number.class.getCanonicalName());

        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> I18nConversionCategory.stringToI18nConversionCategory("currency"))
                .withMessageContaining("Invalid format type currency");
    }

    @Test
    void frameworkEnumsExposeStableConvenienceViews() {
        Assertions.assertThat(LiteralKind.allLiteralKinds())
                .containsExactly(
                        LiteralKind.NULL,
                        LiteralKind.INT,
                        LiteralKind.LONG,
                        LiteralKind.FLOAT,
                        LiteralKind.DOUBLE,
                        LiteralKind.BOOLEAN,
                        LiteralKind.CHAR,
                        LiteralKind.STRING);
        Assertions.assertThat(LiteralKind.primitiveLiteralKinds())
                .containsExactly(
                        LiteralKind.INT,
                        LiteralKind.LONG,
                        LiteralKind.FLOAT,
                        LiteralKind.DOUBLE,
                        LiteralKind.BOOLEAN,
                        LiteralKind.CHAR);

        Assertions.assertThat(TypeUseLocation.values())
                .contains(TypeUseLocation.FIELD, TypeUseLocation.PARAMETER, TypeUseLocation.RETURN, TypeUseLocation.ALL);
        Assertions.assertThat(TypeKind.values())
                .contains(TypeKind.BOOLEAN, TypeKind.DECLARED, TypeKind.ARRAY, TypeKind.INTERSECTION);
        Assertions.assertThat(Pure.Kind.values())
                .containsExactly(Pure.Kind.SIDE_EFFECT_FREE, Pure.Kind.DETERMINISTIC);
        Assertions.assertThat(Prefix.values())
                .contains(Prefix.yotta, Prefix.kilo, Prefix.one, Prefix.micro, Prefix.yocto);
        Assertions.assertThat(Prefix.valueOf("micro")).isEqualTo(Prefix.micro);
    }

    @Test
    void checkerQualTypesIntegrateWithOrdinaryCodeWithoutReflection() {
        QualifiedEndpointBuilder builder = new QualifiedEndpointBuilder();

        Assertions.assertThat(builder.maybeHost()).isEmpty();

        String endpoint = builder
                .host("metadata.graalvm.org")
                .port(443)
                .addSegment("checker-qual")
                .addSegment("3.12.0")
                .addAllowedPattern("(checker-qual)")
                .build();

        String person = String.format(Locale.ROOT, PERSON_TEMPLATE, "segments", builder.segmentCount());
        String report = MessageFormat.format(REPORT_TEMPLATE, new Date(0L), builder.segmentCount());

        Assertions.assertThat(endpoint).isEqualTo("https://metadata.graalvm.org:443/checker-qual/3.12.0");
        Assertions.assertThat(builder.requiredHost()).contains("metadata.graalvm.org");
        Assertions.assertThat(builder.segmentCount()).isEqualTo(2);
        Assertions.assertThat(builder.allowedPatternCount()).isEqualTo(1);
        Assertions.assertThat(person).isEqualTo("segments:2");
        Assertions.assertThat(report).startsWith("1970-01-01 -> 2");
        Assertions.assertThat(Distance.asKilometers(1500L)).isEqualTo(1.5d);
        Assertions.assertThat(STRING_BINARY_NAME).isEqualTo("java.lang.String");
        Assertions.assertThat(ENTRY_FQ_NAME).isEqualTo("java.util.Map.Entry");
        Assertions.assertThat(DEFAULT_SCHEME).isEqualTo("https");
    }

    @AnnotatedFor({"nullness", "calledmethods", "formatter", "i18nformatter", "units"})
    private static final class QualifiedEndpointBuilder {
        private final List<String> segments = new ArrayList<>();
        private final ReentrantLock lock = new ReentrantLock();

        @GuardedBy("lock")
        private final List<@Regex(1) String> allowedPatterns = new ArrayList<>();

        @MonotonicNonNull
        private String host;

        private int port = 443;

        @EnsuresCalledMethods(value = "this", methods = {"host"})
        private @This QualifiedEndpointBuilder host(@NonNull @Untainted String value) {
            this.host = value.strip();
            return this;
        }

        @EnsuresCalledMethods(value = "this", methods = {"port"})
        private @This QualifiedEndpointBuilder port(@IntRange(from = 1, to = 65535) int value) {
            this.port = value;
            return this;
        }

        private @This QualifiedEndpointBuilder addSegment(@MinLen(1) @Untainted String segment) {
            this.segments.add(segment);
            return this;
        }

        private @This QualifiedEndpointBuilder addAllowedPattern(@Regex(1) String pattern) {
            this.lock.lock();
            try {
                return addAllowedPatternWhileLocked(pattern);
            } finally {
                this.lock.unlock();
            }
        }

        @Holding("lock")
        private @This QualifiedEndpointBuilder addAllowedPatternWhileLocked(@Regex(1) String pattern) {
            this.allowedPatterns.add(pattern);
            return this;
        }

        @Pure
        @Deterministic
        private @Unsigned int segmentCount() {
            return this.segments.size();
        }

        private @MaybePresent Optional<@NonNull String> maybeHost() {
            return Optional.ofNullable(this.host);
        }

        @SideEffectFree
        private @Present Optional<@NonNull String> requiredHost(@CalledMethods("host") QualifiedEndpointBuilder this) {
            return Optional.of(this.host);
        }

        @EnsuresNonNull("host")
        private void ensureHost() {
            if (this.host == null) {
                throw new IllegalStateException("host must be set");
            }
        }

        @EnsuresHost(value = "host")
        private String build(@CalledMethods({"host", "port"}) QualifiedEndpointBuilder this) {
            ensureHost();
            return DEFAULT_SCHEME + "://" + this.host + ":" + this.port + "/" + String.join("/", this.segments);
        }

        private int allowedPatternCount() {
            this.lock.lock();
            try {
                return this.allowedPatterns.size();
            } finally {
                this.lock.unlock();
            }
        }
    }

    private static final class Distance {
        private Distance() {
        }

        private static @km double asKilometers(@m long meters) {
            return meters / 1000.0d;
        }
    }

    @SubtypeOf({})
    @QualifierForLiterals(value = {LiteralKind.STRING}, stringPatterns = {"cfg\\..+"})
    @TargetLocations({TypeUseLocation.FIELD, TypeUseLocation.PARAMETER, TypeUseLocation.RETURN})
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
    private @interface ConfigKey {
    }

    @UnitsMultiple(quantity = m.class, prefix = Prefix.kilo)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
    private @interface KilometerUnit {
    }

    @MonotonicQualifier(ConfigKey.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
    private @interface MonotonicConfigKey {
    }

    @PreconditionAnnotation(qualifier = NonNull.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface RequiresHost {
        @org.checkerframework.framework.qual.JavaExpression String[] value();
    }

    @PostconditionAnnotation(qualifier = NonNull.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface EnsuresHost {
        @org.checkerframework.framework.qual.JavaExpression String[] value();
    }

    @ConditionalPostconditionAnnotation(qualifier = NonNull.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface EnsuresHostIf {
        boolean result();

        @org.checkerframework.framework.qual.JavaExpression String[] expression();
    }
}
