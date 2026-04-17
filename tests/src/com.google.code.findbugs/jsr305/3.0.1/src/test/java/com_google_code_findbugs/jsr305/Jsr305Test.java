/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_findbugs.jsr305;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.CheckForSigned;
import javax.annotation.CheckReturnValue;
import javax.annotation.Detainted;
import javax.annotation.MatchesPattern;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.ParametersAreNullableByDefault;
import javax.annotation.PropertyKey;
import javax.annotation.RegEx;
import javax.annotation.Signed;
import javax.annotation.Syntax;
import javax.annotation.Tainted;
import javax.annotation.Untainted;
import javax.annotation.WillClose;
import javax.annotation.WillCloseWhenClosed;
import javax.annotation.WillNotClose;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import javax.annotation.meta.Exclusive;
import javax.annotation.meta.Exhaustive;
import javax.annotation.meta.TypeQualifier;
import javax.annotation.meta.TypeQualifierDefault;
import javax.annotation.meta.TypeQualifierNickname;
import javax.annotation.meta.TypeQualifierValidator;
import javax.annotation.meta.When;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Jsr305Test {
    @Test
    void checkerImplementationsClassifyConstantsAcrossNullabilityAndNumericContracts() {
        TypeQualifierValidator<Nonnull> nonnullChecker = new Nonnull.Checker();
        TypeQualifierValidator<Nonnegative> nonnegativeChecker = new Nonnegative.Checker();

        assertThat(nonnullChecker.forConstantValue(nonnull(When.ALWAYS), "metadata"))
                .isEqualTo(When.ALWAYS);
        assertThat(nonnullChecker.forConstantValue(nonnull(When.ALWAYS), null))
                .isEqualTo(When.NEVER);

        assertThat(nonnegativeChecker.forConstantValue(nonnegative(When.ALWAYS), 0))
                .isEqualTo(When.ALWAYS);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(When.ALWAYS), 7L))
                .isEqualTo(When.ALWAYS);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(When.ALWAYS), 3.5F))
                .isEqualTo(When.ALWAYS);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(When.ALWAYS), BigDecimal.valueOf(12)))
                .isEqualTo(When.ALWAYS);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(When.ALWAYS), -1))
                .isEqualTo(When.NEVER);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(When.ALWAYS), -2.5D))
                .isEqualTo(When.NEVER);
        assertThat(nonnegativeChecker.forConstantValue(nonnegative(When.ALWAYS), "not-a-number"))
                .isEqualTo(When.NEVER);
    }

    @Test
    void nonnegativeCheckerTreatsFloatingPointNegativeZeroAsNonnegative() {
        TypeQualifierValidator<Nonnegative> nonnegativeChecker = new Nonnegative.Checker();
        Nonnegative annotation = nonnegative(When.ALWAYS);

        assertThat(nonnegativeChecker.forConstantValue(annotation, -0.0D))
                .isEqualTo(When.ALWAYS);
        assertThat(nonnegativeChecker.forConstantValue(annotation, -0.0F))
                .isEqualTo(When.ALWAYS);
    }

    @Test
    void regexRelatedCheckersRespectPatternFlagsAndInvalidExpressions() {
        TypeQualifierValidator<MatchesPattern> matchesPatternChecker = new MatchesPattern.Checker();
        TypeQualifierValidator<RegEx> regExChecker = new RegEx.Checker();

        assertThat(matchesPatternChecker.forConstantValue(
                matchesPattern("graalvm|native-image", Pattern.CASE_INSENSITIVE),
                "GRAALVM"))
                .isEqualTo(When.ALWAYS);
        assertThat(matchesPatternChecker.forConstantValue(
                matchesPattern("graalvm|native-image", Pattern.CASE_INSENSITIVE),
                "metadata"))
                .isEqualTo(When.NEVER);

        assertThat(regExChecker.forConstantValue(regEx(When.ALWAYS), "[a-z]+-[0-9]+"))
                .isEqualTo(When.ALWAYS);
        assertThat(regExChecker.forConstantValue(regEx(When.ALWAYS), "[a-z+"))
                .isEqualTo(When.NEVER);
        assertThat(regExChecker.forConstantValue(regEx(When.ALWAYS), 42))
                .isEqualTo(When.NEVER);
    }

    @Test
    void annotationInstancesExposeConfiguredMembersAndMarkerTypesRemainUsable() {
        CheckReturnValue checkReturnValue = checkReturnValue(When.MAYBE);
        PropertyKey propertyKey = propertyKey(When.ALWAYS);
        Syntax syntax = syntax("RegEx", When.UNKNOWN);
        Untainted untainted = untainted(When.MAYBE);
        GuardedBy guardedBy = guardedBy("this");
        TypeQualifier typeQualifier = typeQualifier(CharSequence.class);
        TypeQualifierDefault typeQualifierDefault = typeQualifierDefault(ElementType.PARAMETER, ElementType.METHOD);
        TypeQualifierNickname typeQualifierNickname = typeQualifierNickname();

        assertThat(When.values()).containsExactly(When.ALWAYS, When.UNKNOWN, When.MAYBE, When.NEVER);
        assertThat(When.valueOf("MAYBE")).isSameAs(When.MAYBE);

        assertThat(checkReturnValue.when()).isEqualTo(When.MAYBE);
        assertThat(checkReturnValue.annotationType()).isSameAs(CheckReturnValue.class);
        assertThat(propertyKey.when()).isEqualTo(When.ALWAYS);
        assertThat(propertyKey.annotationType()).isSameAs(PropertyKey.class);
        assertThat(syntax.value()).isEqualTo("RegEx");
        assertThat(syntax.when()).isEqualTo(When.UNKNOWN);
        assertThat(syntax.annotationType()).isSameAs(Syntax.class);
        assertThat(untainted.when()).isEqualTo(When.MAYBE);
        assertThat(untainted.annotationType()).isSameAs(Untainted.class);
        assertThat(guardedBy.value()).isEqualTo("this");
        assertThat(guardedBy.annotationType()).isSameAs(GuardedBy.class);
        assertThat(typeQualifier.applicableTo()).isSameAs(CharSequence.class);
        assertThat(typeQualifier.annotationType()).isSameAs(TypeQualifier.class);
        assertThat(typeQualifierDefault.value()).containsExactly(ElementType.PARAMETER, ElementType.METHOD);
        assertThat(typeQualifierDefault.annotationType()).isSameAs(TypeQualifierDefault.class);
        assertThat(typeQualifierNickname.annotationType()).isSameAs(TypeQualifierNickname.class);

        assertThat(List.of(
                CheckForNull.class,
                CheckForSigned.class,
                Nullable.class,
                Signed.class,
                Detainted.class,
                Tainted.class,
                WillClose.class,
                WillCloseWhenClosed.class,
                WillNotClose.class,
                ParametersAreNonnullByDefault.class,
                ParametersAreNullableByDefault.class,
                ThreadSafe.class,
                NotThreadSafe.class,
                Immutable.class,
                Exclusive.class,
                Exhaustive.class
        )).hasSize(16);
    }

    @Test
    void annotatedComponentsExecuteNormallyInRegularJavaCode() throws IOException {
        GuardedCounter counter = new GuardedCounter();
        PatternCatalog catalog = new PatternCatalog();
        NullablePatternCatalog nullableCatalog = new NullablePatternCatalog();
        TrackedCloseable delegate = new TrackedCloseable();
        ExtendedManagedCloser closer = new ExtendedManagedCloser(delegate);
        TrackedInputStream previewSource = new TrackedInputStream("metadata-forge");
        TrackedInputStream drainSource = new TrackedInputStream("native-image");

        assertThat(counter.increment()).isEqualTo(1);
        assertThat(counter.increment()).isEqualTo(2);
        assertThat(counter.current()).isEqualTo(2);

        assertThat(catalog.message("library.name")).isEqualTo("jsr305");
        assertThat(catalog.find("missing")).isNull();
        assertThat(catalog.matches("[a-z0-9-]+", "metadata-forge")).isTrue();
        assertThat(catalog.matches("[a-z0-9-]+", "Metadata Forge")).isFalse();
        assertThat(catalog.matches("[a-z0-9-]+", null)).isFalse();
        assertThat(catalog.sanitize("<safe-value>"))
                .isEqualTo("safe-value");
        assertThat(catalog.normalize("  jsr305  "))
                .isEqualTo("jsr305");
        assertThat(catalog.add(-2, 5)).isEqualTo(3);
        assertThat(catalog.describeDefaultParameterPolicy("jsr305"))
                .isEqualTo("nonnull-default:jsr305");
        assertThat(nullableCatalog.describeNullableParameterPolicy(null))
                .isEqualTo("nullable-default:<missing>");

        assertThat(closer.preview(previewSource, 8)).isEqualTo("metadata");
        assertThat(previewSource.isClosed()).isFalse();
        assertThat(closer.drainAndClose(drainSource)).isEqualTo("native-image");
        assertThat(drainSource.isClosed()).isTrue();

        closer.close();

        assertThat(closer.isClosed()).isTrue();
        assertThat(closer.isSubclassClosed()).isTrue();
        assertThat(delegate.isClosed()).isTrue();
    }

    private static Nonnull nonnull(When when) {
        return new Nonnull() {
            @Override
            public When when() {
                return when;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Nonnull.class;
            }
        };
    }

    private static Nonnegative nonnegative(When when) {
        return new Nonnegative() {
            @Override
            public When when() {
                return when;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Nonnegative.class;
            }
        };
    }

    private static MatchesPattern matchesPattern(String value, int flags) {
        return new MatchesPattern() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public int flags() {
                return flags;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return MatchesPattern.class;
            }
        };
    }

    private static RegEx regEx(When when) {
        return new RegEx() {
            @Override
            public When when() {
                return when;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RegEx.class;
            }
        };
    }

    private static CheckReturnValue checkReturnValue(When when) {
        return new CheckReturnValue() {
            @Override
            public When when() {
                return when;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return CheckReturnValue.class;
            }
        };
    }

    private static PropertyKey propertyKey(When when) {
        return new PropertyKey() {
            @Override
            public When when() {
                return when;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return PropertyKey.class;
            }
        };
    }

    private static Syntax syntax(String value, When when) {
        return new Syntax() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public When when() {
                return when;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Syntax.class;
            }
        };
    }

    private static Untainted untainted(When when) {
        return new Untainted() {
            @Override
            public When when() {
                return when;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Untainted.class;
            }
        };
    }

    private static GuardedBy guardedBy(String value) {
        return new GuardedBy() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return GuardedBy.class;
            }
        };
    }

    private static TypeQualifier typeQualifier(Class<?> applicableTo) {
        return new TypeQualifier() {
            @Override
            public Class<?> applicableTo() {
                return applicableTo;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return TypeQualifier.class;
            }
        };
    }

    private static TypeQualifierDefault typeQualifierDefault(ElementType... elementTypes) {
        return new TypeQualifierDefault() {
            @Override
            public ElementType[] value() {
                return elementTypes;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return TypeQualifierDefault.class;
            }
        };
    }

    private static TypeQualifierNickname typeQualifierNickname() {
        return new TypeQualifierNickname() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return TypeQualifierNickname.class;
            }
        };
    }

    @ThreadSafe
    private static final class GuardedCounter {
        @GuardedBy("this")
        private int value;

        synchronized int increment() {
            return ++value;
        }

        @CheckReturnValue
        synchronized int current() {
            return value;
        }
    }

    @SlugDefaults
    @ParametersAreNonnullByDefault
    private static final class PatternCatalog {
        @SlugPattern
        private final String slugPattern = "[a-z0-9-]+";

        @ExclusiveOptions
        private final List<String> mutableStates = List.of("draft", "published");

        @ExhaustiveOptions
        private final List<String> allStates = List.of("draft", "published", "archived");

        private final Map<String, String> messages = Map.of(
                "library.name", "jsr305",
                "library.mode", "native-image");

        @CheckReturnValue
        String message(@PropertyKey String key) {
            return messages.get(key);
        }

        @CheckForNull
        String find(@PropertyKey String key) {
            return messages.get(key);
        }

        @CheckReturnValue
        boolean matches(@Syntax("RegEx") @RegEx String regex, @CheckForNull String candidate) {
            return candidate != null && Pattern.compile(regex).matcher(candidate).matches();
        }

        @Untainted(when = When.MAYBE)
        String sanitize(@Tainted String value) {
            return value.replace("<", "").replace(">", "");
        }

        @Detainted
        @SlugValue
        String normalize(@Detainted String value) {
            return value.trim();
        }

        @Signed
        int add(@Signed int left, @CheckForSigned int right) {
            return left + right;
        }

        @CheckReturnValue
        String describeDefaultParameterPolicy(String libraryName) {
            return "nonnull-default:" + libraryName;
        }
    }

    @ParametersAreNullableByDefault
    private static final class NullablePatternCatalog {
        String describeNullableParameterPolicy(String libraryName) {
            return "nullable-default:" + (libraryName == null ? "<missing>" : libraryName);
        }
    }

    @NotThreadSafe
    private static class ManagedCloser implements Closeable {
        private final Closeable delegate;
        private boolean closed;

        ManagedCloser(@WillCloseWhenClosed Closeable delegate) {
            this.delegate = delegate;
        }

        @CheckReturnValue
        String preview(@WillNotClose TrackedInputStream input, int length) throws IOException {
            byte[] bytes = input.readNBytes(length);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        @CheckReturnValue
        String drainAndClose(@WillClose TrackedInputStream input) throws IOException {
            try (TrackedInputStream closeable = input) {
                return new String(closeable.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        @Override
        @OverridingMethodsMustInvokeSuper
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }

        boolean isClosed() {
            return closed;
        }
    }

    private static final class ExtendedManagedCloser extends ManagedCloser {
        private boolean subclassClosed;

        private ExtendedManagedCloser(Closeable delegate) {
            super(delegate);
        }

        @Override
        public void close() throws IOException {
            super.close();
            subclassClosed = true;
        }

        private boolean isSubclassClosed() {
            return subclassClosed;
        }
    }

    @NotThreadSafe
    private static final class TrackedCloseable implements Closeable {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }

        private boolean isClosed() {
            return closed;
        }
    }

    @NotThreadSafe
    private static final class TrackedInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackedInputStream(String value) {
            super(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private boolean isClosed() {
            return closed;
        }
    }

    @TypeQualifier(applicableTo = CharSequence.class)
    private @interface SlugValue {
    }

    @TypeQualifierNickname
    @RegEx
    private @interface SlugPattern {
    }

    @TypeQualifierDefault({ElementType.PARAMETER, ElementType.METHOD})
    private @interface SlugDefaults {
    }

    @Exclusive
    private @interface ExclusiveOptions {
    }

    @Exhaustive
    private @interface ExhaustiveOptions {
    }
}
