/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_checkerframework.checker_compat_qual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

import static org.assertj.core.api.Assertions.assertThat;

class Checker_compat_qualTest {
    @Test
    void usesTypeUseAnnotationsInGenericFieldsParametersAndReturns() {
        TypeUseFixture<String> fixture = new TypeUseFixture<>();

        fixture.put("alpha", "A");
        fixture.put("beta", "B");
        fixture.initializeCurrent("ready");

        assertThat(fixture.find("alpha")).isEqualTo("A");
        assertThat(fixture.find("missing")).isNull();
        assertThat(fixture.identity("echo")).isEqualTo("echo");
        assertThat(fixture.identity(null)).isNull();
        assertThat(fixture.snapshot()).containsExactly("A", "B");
        assertThat(fixture.current()).isEqualTo("ready");
    }

    @Test
    void usesDeclarationAnnotationsOnFieldsMethodsParametersAndLocals() {
        DeclarationFixture fixture = new DeclarationFixture();

        fixture.put("primary", "first");
        fixture.put("secondary", "second");
        fixture.initialize("configured");

        assertThat(fixture.find("primary")).isEqualTo("first");
        assertThat(fixture.find("missing")).isNull();
        assertThat(fixture.identity("value")).isEqualTo("value");
        assertThat(fixture.identity(null)).isNull();
        assertThat(fixture.valueForPrimaryKey()).isEqualTo("first");
        assertThat(fixture.initializedValue()).isEqualTo("configured");
    }

    private static final class TypeUseFixture<@NonNullType T> {
        private final Map<String, T> values = new LinkedHashMap<>();
        private @MonotonicNonNullType T currentValue;

        private void put(@KeyForType("values") String key, @NonNullType T value) {
            values.put(key, value);
        }

        private @NullableType T find(@NonNullType String key) {
            return values.get(key);
        }

        private @PolyNullType T identity(@PolyNullType T value) {
            return value;
        }

        private void initializeCurrent(@NonNullType T value) {
            currentValue = value;
        }

        private @NonNullType List<@NonNullType T> snapshot() {
            return new ArrayList<>(values.values());
        }

        private @NonNullType T current() {
            return currentValue;
        }
    }

    private static final class DeclarationFixture {
        private final Map<String, String> values = new LinkedHashMap<>();
        private @MonotonicNonNullDecl String initializedValue;

        private void put(@NonNullDecl String key, @NonNullDecl String value) {
            values.put(key, value);
        }

        private @NullableDecl String find(@NonNullDecl String key) {
            return values.get(key);
        }

        private @PolyNullDecl String identity(@PolyNullDecl String value) {
            return value;
        }

        private void initialize(@NonNullDecl String value) {
            initializedValue = value;
        }

        private @NonNullDecl String valueForPrimaryKey() {
            @KeyForDecl("values") String key = "primary";
            return values.get(key);
        }

        private @NonNullDecl String initializedValue() {
            return initializedValue;
        }
    }
}
