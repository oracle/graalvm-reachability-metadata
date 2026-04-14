/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_checkerframework.checker_compat_qual;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Checker_compat_qualTest {

    @Test
    void declarationAnnotationsCanBeUsedOnCommonDeclarationTargets() {
        DeclarationFixture fixture = new DeclarationFixture(null, "primary");

        fixture.initialize("ready");
        fixture.register();
        fixture.registerAlias("secondary");

        assertEquals("primary", fixture.choose("primary"));
        assertNull(fixture.choose(null));
        assertEquals("ready", fixture.getLateInit());
        assertEquals("primary", fixture.getCache().get("primary"));
        assertEquals("secondary", fixture.getCache().get("secondary"));
    }

    @Test
    void typeAnnotationsCanBeUsedAcrossTypeUsePositions() {
        TypeUsageFixture<String> fixture = new TypeUsageFixture<>("primary");

        fixture.accept(null);
        fixture.accept("secondary");
        fixture.register();

        assertEquals("primary", fixture.first());
        assertNull(fixture.second());
        assertEquals("secondary", fixture.lastValueAsString());
        assertEquals("primary", fixture.render(List.of("primary")));
        assertEquals("primary", fixture.getCache().get(fixture.primaryKey()));
    }

    @Test
    void keyForDeclarationAnnotationExposesItsValueElement() {
        KeyForDecl annotation = new KeyForDecl() {
            @Override
            public String[] value() {
                return new String[] {"cache", "fallback"};
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return KeyForDecl.class;
            }
        };

        assertArrayEquals(new String[] {"cache", "fallback"}, annotation.value());
        assertSame(KeyForDecl.class, annotation.annotationType());
    }

    @Test
    void keyForTypeAnnotationExposesItsValueElement() {
        KeyForType annotation = new KeyForType() {
            @Override
            public String[] value() {
                return new String[] {"cache", "archive"};
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return KeyForType.class;
            }
        };

        assertArrayEquals(new String[] {"cache", "archive"}, annotation.value());
        assertSame(KeyForType.class, annotation.annotationType());
    }

    @Test
    void typeAnnotationsCanBeUsedOnReceiversCastsThrowsAndArrayCreation() {
        AdvancedTypeUsageFixture fixture = new AdvancedTypeUsageFixture();

        fixture.capture("  primary  ");
        fixture.captureAll("secondary", "backup");

        assertEquals("primary", fixture.firstCaptured());
        assertEquals("secondary", fixture.current());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.capture(null));
        assertEquals("rawValue", exception.getMessage());
    }

    @Test
    void compatAnnotationsCanBeUsedOnRecordComponents() {
        RecordFixture fixture = new RecordFixture("  primary  ", List.of("secondary", "backup"), null);

        assertEquals("primary", fixture.normalizedPrimary());
        assertEquals("secondary", fixture.firstAlias());
        assertNull(fixture.optionalTag());
    }

    @Test
    void typeAnnotationsCanBeUsedInPatternMatchingBranches() {
        assertEquals("secondary", RecordFixture.normalize("  secondary  "));
        assertNull(RecordFixture.normalize(42));
    }

    @NonNullDecl
    private static final class DeclarationFixture {

        @NullableDecl
        private final String optionalField;

        @MonotonicNonNullDecl
        private String lateInit;

        private final Map<String, String> cache = new HashMap<>();

        @KeyForDecl("cache")
        private final String primaryKey;

        @PolyNullDecl
        private DeclarationFixture(@NullableDecl String optionalField, @KeyForDecl("cache") String primaryKey) {
            this.optionalField = optionalField;
            this.primaryKey = primaryKey;
        }

        @PolyNullDecl
        private String choose(@PolyNullDecl String candidate) {
            @NullableDecl String localChoice = candidate;
            return localChoice == null ? optionalField : localChoice;
        }

        private void initialize(@NonNullDecl String value) {
            lateInit = value;
        }

        private void register() {
            cache.put(primaryKey, choose(primaryKey));
        }

        private void registerAlias(@KeyForDecl("cache") String alias) {
            cache.put(alias, alias);
        }

        private String getLateInit() {
            return lateInit;
        }

        private Map<String, String> getCache() {
            return cache;
        }
    }

    private static final class AdvancedTypeUsageFixture {

        private final List<String> captured = new ArrayList<>();
        private String current = "unset";

        private void capture(@NonNullType AdvancedTypeUsageFixture this, @NullableType Object rawValue)
                throws @NonNullType IllegalArgumentException {
            String normalized = requireString(rawValue);
            captured.add(normalized);
            current = normalized;
        }

        private void captureAll(Object first, Object second) {
            @NonNullType String[] values = new @NonNullType String[] {
                    requireString(first),
                    requireString(second)
            };
            captured.add(values[0]);
            current = values[0];
        }

        private static String requireString(@NullableType Object rawValue)
                throws @NonNullType IllegalArgumentException {
            if (rawValue == null) {
                throw new IllegalArgumentException("rawValue");
            }
            return ((@NonNullType String) rawValue).strip();
        }

        private String firstCaptured() {
            return captured.get(0);
        }

        private String current() {
            return current;
        }
    }

    private static final class TypeUsageFixture<@NonNullType T extends @NullableType CharSequence> {

        private final List<@PolyNullType T> values = new ArrayList<>();
        private @MonotonicNonNullType T lastValue;
        private final Map<String, String> cache = new HashMap<>();
        private final @KeyForType("cache") String primaryKey = "primary";

        private TypeUsageFixture(@PolyNullType T first) {
            values.add(first);
            lastValue = first;
        }

        private @NonNullType T first() {
            return values.get(0);
        }

        private void accept(@NullableType T value) {
            @NullableType T local = value;
            values.add(local);
            if (local != null) {
                lastValue = local;
            }
        }

        private @NullableType T second() {
            return values.size() > 1 ? values.get(1) : null;
        }

        private CharSequence render(List<? extends @NonNullType CharSequence> input) {
            return input.get(0);
        }

        private void register() {
            cache.put(primaryKey, render(List.of(first())).toString());
        }

        private String lastValueAsString() {
            return lastValue.toString();
        }

        private Map<String, String> getCache() {
            return cache;
        }

        private @KeyForType("cache") String primaryKey() {
            return primaryKey;
        }
    }

    private record RecordFixture(
            @NonNullType String primary,
            List<@NonNullType String> aliases,
            @NullableDecl String optionalTag) {

        private @NonNullType String normalizedPrimary() {
            return primary.strip();
        }

        private @NonNullType String firstAlias() {
            return aliases.get(0);
        }

        private static @NullableType String normalize(@NullableType Object candidate) {
            if (candidate instanceof @NonNullType String text) {
                return text.strip();
            }
            return null;
        }
    }
}
