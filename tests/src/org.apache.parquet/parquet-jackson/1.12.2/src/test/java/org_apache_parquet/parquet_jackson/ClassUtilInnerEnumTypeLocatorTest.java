/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.util.ClassUtil;

public class ClassUtilInnerEnumTypeLocatorTest {
    @Test
    void locatesEnumTypeFromEmptyEnumSetMetadata() {
        EnumSet<EncodingMode> emptyModes = EnumSet.noneOf(EncodingMode.class);

        assertResolvedEnumType(() -> ClassUtil.findEnumType(emptyModes));
    }

    @Test
    void locatesEnumTypeFromEmptyEnumMapMetadata() {
        EnumMap<EncodingMode, String> emptyNames = new EnumMap<>(EncodingMode.class);

        assertResolvedEnumType(() -> ClassUtil.findEnumType(emptyNames));
    }

    private static void assertResolvedEnumType(EnumTypeLookup lookup) {
        try {
            Class<? extends Enum<?>> enumType = lookup.findEnumType();

            assertThat(enumType).isSameAs(EncodingMode.class);
        } catch (IllegalArgumentException ex) {
            assertThat(ex).hasCauseInstanceOf(IllegalAccessException.class);
        }
    }

    private enum EncodingMode {
        PLAIN,
        DICTIONARY
    }

    @FunctionalInterface
    private interface EnumTypeLookup {
        Class<? extends Enum<?>> findEnumType();
    }
}
