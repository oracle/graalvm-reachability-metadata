/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_findbugs.jsr305;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
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

public class Jsr305Test {
    @Test
    void nonnullCheckerAcceptsOnlyNonNullConstants() {
        TypeQualifierValidator<Nonnull> checker = new Nonnull.Checker();
        Nonnull alwaysNonnull = nonnull(When.ALWAYS);
        Nonnull maybeNonnull = nonnull(When.MAYBE);

        assertThat(alwaysNonnull.annotationType()).isEqualTo(Nonnull.class);
        assertThat(alwaysNonnull.when()).isEqualTo(When.ALWAYS);
        assertThat(maybeNonnull.when()).isEqualTo(When.MAYBE);
        assertThat(checker.forConstantValue(alwaysNonnull, "value")).isEqualTo(When.ALWAYS);
        assertThat(checker.forConstantValue(alwaysNonnull, 42)).isEqualTo(When.ALWAYS);
        assertThat(checker.forConstantValue(alwaysNonnull, null)).isEqualTo(When.NEVER);
    }

    @Test
    void nonnegativeCheckerHandlesNumberFamiliesAndRejectsOtherValues() {
        TypeQualifierValidator<Nonnegative> checker = new Nonnegative.Checker();
        Nonnegative qualifier = nonnegative(When.ALWAYS);

        assertThat(qualifier.annotationType()).isEqualTo(Nonnegative.class);
        assertThat(qualifier.when()).isEqualTo(When.ALWAYS);
        assertThat(checker.forConstantValue(qualifier, 0)).isEqualTo(When.ALWAYS);
        assertThat(checker.forConstantValue(qualifier, 12L)).isEqualTo(When.ALWAYS);
        assertThat(checker.forConstantValue(qualifier, 1.25D)).isEqualTo(When.ALWAYS);
        assertThat(checker.forConstantValue(qualifier, 0.5F)).isEqualTo(When.ALWAYS);
        assertThat(checker.forConstantValue(qualifier, (short) 7)).isEqualTo(When.ALWAYS);
        assertThat(checker.forConstantValue(qualifier, -1)).isEqualTo(When.NEVER);
        assertThat(checker.forConstantValue(qualifier, -9L)).isEqualTo(When.NEVER);
        assertThat(checker.forConstantValue(qualifier, -0.25D)).isEqualTo(When.NEVER);
        assertThat(checker.forConstantValue(qualifier, -2.5F)).isEqualTo(When.NEVER);
        assertThat(checker.forConstantValue(qualifier, "not a number")).isEqualTo(When.NEVER);
        assertThat(checker.forConstantValue(qualifier, null)).isEqualTo(When.NEVER);
    }

    @Test
    void regexCheckerValidatesPatternSyntaxConstants() {
        TypeQualifierValidator<RegEx> checker = new RegEx.Checker();
        RegEx regex = regex(When.ALWAYS);

        assertThat(regex.annotationType()).isEqualTo(RegEx.class);
        assertThat(regex.when()).isEqualTo(When.ALWAYS);
        assertThat(checker.forConstantValue(regex, "[a-z]+\\d{2}")).isEqualTo(When.ALWAYS);
        assertThat(checker.forConstantValue(regex, "(")).isEqualTo(When.NEVER);
        assertThat(checker.forConstantValue(regex, 123)).isEqualTo(When.NEVER);
        assertThat(checker.forConstantValue(regex, null)).isEqualTo(When.NEVER);
    }

    @Test
    void matchesPatternCheckerUsesConfiguredPatternAndFlags() {
        TypeQualifierValidator<MatchesPattern> checker = new MatchesPattern.Checker();
        MatchesPattern caseInsensitiveCode = matchesPattern("item-[a-z]+", Pattern.CASE_INSENSITIVE);
        MatchesPattern digitsOnly = matchesPattern("\\d+", 0);

        assertThat(caseInsensitiveCode.annotationType()).isEqualTo(MatchesPattern.class);
        assertThat(caseInsensitiveCode.value()).isEqualTo("item-[a-z]+");
        assertThat(caseInsensitiveCode.flags()).isEqualTo(Pattern.CASE_INSENSITIVE);
        assertThat(checker.forConstantValue(caseInsensitiveCode, "ITEM-abc")).isEqualTo(When.ALWAYS);
        assertThat(checker.forConstantValue(caseInsensitiveCode, "prefix-abc")).isEqualTo(When.NEVER);
        assertThat(checker.forConstantValue(digitsOnly, "12345")).isEqualTo(When.ALWAYS);
        assertThat(checker.forConstantValue(digitsOnly, "123-45")).isEqualTo(When.NEVER);
    }

    @Test
    void qualifierAnnotationsExposeElementValues() {
        assertThat(checkReturnValue(When.NEVER).when()).isEqualTo(When.NEVER);
        assertThat(propertyKey(When.UNKNOWN).when()).isEqualTo(When.UNKNOWN);
        assertThat(syntax("SQL:dialect=PostgreSQL", When.MAYBE).value()).isEqualTo("SQL:dialect=PostgreSQL");
        assertThat(syntax("Java", When.ALWAYS).when()).isEqualTo(When.ALWAYS);
        assertThat(untainted(When.ALWAYS).when()).isEqualTo(When.ALWAYS);
        assertThat(guardedBy("lock").value()).isEqualTo("lock");
        assertThat(typeQualifier(String.class).applicableTo()).isEqualTo(String.class);
        assertThat(typeQualifierDefault(ElementType.METHOD, ElementType.PARAMETER).value())
                .containsExactly(ElementType.METHOD, ElementType.PARAMETER);
    }

    @Test
    void markerAnnotationsExposeTheirAnnotationTypes() {
        assertThat(marker(CheckForNull.class).annotationType()).isEqualTo(CheckForNull.class);
        assertThat(marker(CheckForSigned.class).annotationType()).isEqualTo(CheckForSigned.class);
        assertThat(marker(Detainted.class).annotationType()).isEqualTo(Detainted.class);
        assertThat(marker(Immutable.class).annotationType()).isEqualTo(Immutable.class);
        assertThat(marker(NotThreadSafe.class).annotationType()).isEqualTo(NotThreadSafe.class);
        assertThat(marker(Nullable.class).annotationType()).isEqualTo(Nullable.class);
        assertThat(marker(OverridingMethodsMustInvokeSuper.class).annotationType())
                .isEqualTo(OverridingMethodsMustInvokeSuper.class);
        assertThat(marker(ParametersAreNonnullByDefault.class).annotationType())
                .isEqualTo(ParametersAreNonnullByDefault.class);
        assertThat(marker(ParametersAreNullableByDefault.class).annotationType())
                .isEqualTo(ParametersAreNullableByDefault.class);
        assertThat(marker(Signed.class).annotationType()).isEqualTo(Signed.class);
        assertThat(marker(Tainted.class).annotationType()).isEqualTo(Tainted.class);
        assertThat(marker(ThreadSafe.class).annotationType()).isEqualTo(ThreadSafe.class);
        assertThat(marker(WillClose.class).annotationType()).isEqualTo(WillClose.class);
        assertThat(marker(WillCloseWhenClosed.class).annotationType()).isEqualTo(WillCloseWhenClosed.class);
        assertThat(marker(WillNotClose.class).annotationType()).isEqualTo(WillNotClose.class);
        assertThat(marker(Exclusive.class).annotationType()).isEqualTo(Exclusive.class);
        assertThat(marker(Exhaustive.class).annotationType()).isEqualTo(Exhaustive.class);
        assertThat(marker(TypeQualifierNickname.class).annotationType()).isEqualTo(TypeQualifierNickname.class);
    }

    @Test
    void whenEnumKeepsDocumentedQualifierRelationshipValues() {
        assertThat(When.values()).containsExactly(When.ALWAYS, When.UNKNOWN, When.MAYBE, When.NEVER);
        assertThat(When.valueOf("ALWAYS")).isEqualTo(When.ALWAYS);
        assertThat(When.valueOf("UNKNOWN")).isEqualTo(When.UNKNOWN);
        assertThat(When.valueOf("MAYBE")).isEqualTo(When.MAYBE);
        assertThat(When.valueOf("NEVER")).isEqualTo(When.NEVER);
    }

    @Test
    void customTypeQualifierCanExposeExclusiveAndExhaustiveState() {
        IdentifierQualifier primaryIdentifier = identifierQualifier("ID", IdentifierKind.PRIMARY);
        IdentifierQualifier secondaryIdentifier = identifierQualifier("ALT", IdentifierKind.SECONDARY);

        assertThat(primaryIdentifier.annotationType()).isEqualTo(IdentifierQualifier.class);
        assertThat(primaryIdentifier.value()).isEqualTo("ID");
        assertThat(primaryIdentifier.kind()).isEqualTo(IdentifierKind.PRIMARY);
        assertThat(secondaryIdentifier.value()).isEqualTo("ALT");
        assertThat(secondaryIdentifier.kind()).isEqualTo(IdentifierKind.SECONDARY);
    }

    @Test
    void annotationsCanBeUsedOnTheirSupportedProgramElements() {
        AnnotatedService service = new AnnotatedService("ready");
        ThreadSafeValue threadSafeValue = new ThreadSafeValue("stable");
        ImmutableValue immutableValue = new ImmutableValue("constant");
        MutableCounter counter = new MutableCounter();

        assertThat(service.format("abc", null, 42, 1)).isEqualTo("ABC:ready");
        assertThat(service.mustBeUsed()).isEqualTo("result");
        service.resourceContract(() -> { }, () -> { }, () -> { }, "select 1");
        assertThat(service.guardedValue()).isEqualTo("ready");
        assertThat(threadSafeValue.value()).isEqualTo("stable");
        assertThat(immutableValue.value()).isEqualTo("constant");
        assertThat(counter.next()).isEqualTo(1);
    }

    @Test
    void resourceOwnershipAnnotationsSupportClosingContracts() throws Exception {
        TrackedResource ownedResource = new TrackedResource("owned");
        TrackedResource borrowedResource = new TrackedResource("borrowed");
        TrackedResource consumedResource = new TrackedResource("consumed");
        ResourceOwner owner = new ResourceOwner(ownedResource);

        assertThat(owner.peek(borrowedResource)).isEqualTo("borrowed");
        assertThat(borrowedResource.isClosed()).isFalse();

        assertThat(owner.consume(consumedResource)).isEqualTo("consumed");
        assertThat(consumedResource.isClosed()).isTrue();
        assertThat(ownedResource.isClosed()).isFalse();

        owner.close();

        assertThat(owner.isClosed()).isTrue();
        assertThat(ownedResource.isClosed()).isTrue();
        assertThat(borrowedResource.isClosed()).isFalse();
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

    private static RegEx regex(When when) {
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
                return elementTypes.clone();
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return TypeQualifierDefault.class;
            }
        };
    }

    private static Annotation marker(Class<? extends Annotation> annotationType) {
        return new Annotation() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return annotationType;
            }
        };
    }

    private static IdentifierQualifier identifierQualifier(String value, IdentifierKind kind) {
        return new IdentifierQualifier() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public IdentifierKind kind() {
                return kind;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return IdentifierQualifier.class;
            }
        };
    }

    @TypeQualifier(applicableTo = CharSequence.class)
    @interface IdentifierQualifier {
        @Exclusive
        String value();

        @Exhaustive
        IdentifierKind kind() default IdentifierKind.PRIMARY;
    }

    @IdentifierQualifier(value = "ID", kind = IdentifierKind.SECONDARY)
    @TypeQualifierNickname
    @interface IdentifierNickname {
    }

    @Nonnull
    @TypeQualifierDefault({ ElementType.METHOD, ElementType.PARAMETER })
    @interface MethodsAndParametersAreNonnull {
    }

    enum IdentifierKind {
        PRIMARY,
        SECONDARY
    }

    @CheckReturnValue
    @ParametersAreNonnullByDefault
    @MethodsAndParametersAreNonnull
    @NotThreadSafe
    private static class AnnotatedService {
        private final Object lock = new Object();

        @GuardedBy("lock")
        private String state;

        @CheckReturnValue(when = When.ALWAYS)
        private AnnotatedService(@Nonnull String state) {
            this.state = state;
        }

        @OverridingMethodsMustInvokeSuper
        @CheckReturnValue(when = When.ALWAYS)
        @MatchesPattern(value = "[A-Z]+:.+", flags = Pattern.CASE_INSENSITIVE)
        @IdentifierNickname
        String format(
                @RegEx String regex,
                @Nullable @CheckForNull Object optional,
                @Nonnegative @CheckForSigned @Signed int count,
                @Untainted @Detainted @Tainted long taintedValue) {
            return regex.toUpperCase() + ":" + guardedValue();
        }

        @CheckReturnValue(when = When.ALWAYS)
        @PropertyKey(when = When.UNKNOWN)
        String mustBeUsed() {
            return "result";
        }

        @GuardedBy("lock")
        String guardedValue() {
            synchronized (lock) {
                return state;
            }
        }

        void resourceContract(
                @WillClose AutoCloseable willClose,
                @WillCloseWhenClosed AutoCloseable willCloseWhenClosed,
                @WillNotClose AutoCloseable willNotClose,
                @Syntax("SQL") String sql) {
            assertThat(willClose).isNotSameAs(willNotClose);
            assertThat(willCloseWhenClosed).isNotNull();
            assertThat(sql).isNotEmpty();
        }
    }

    @ThreadSafe
    private static class ThreadSafeValue {
        private final String value;

        private ThreadSafeValue(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    @Immutable
    private static class ImmutableValue {
        private final String value;

        private ImmutableValue(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    @NotThreadSafe
    @ParametersAreNullableByDefault
    private static class MutableCounter {
        private int value;

        int next() {
            value++;
            return value;
        }
    }

    private static class ResourceOwner implements AutoCloseable {
        private final TrackedResource ownedResource;
        private boolean closed;

        private ResourceOwner(@WillCloseWhenClosed TrackedResource ownedResource) {
            this.ownedResource = ownedResource;
        }

        String peek(@WillNotClose TrackedResource resource) {
            return resource.value();
        }

        String consume(@WillClose TrackedResource resource) throws Exception {
            try (TrackedResource closeableResource = resource) {
                return closeableResource.value();
            }
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() throws Exception {
            closed = true;
            ownedResource.close();
        }
    }

    private static class TrackedResource implements AutoCloseable {
        private final String value;
        private boolean closed;

        private TrackedResource(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
