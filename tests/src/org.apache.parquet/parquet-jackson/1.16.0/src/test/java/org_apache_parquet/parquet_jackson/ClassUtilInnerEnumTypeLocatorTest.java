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
    void locatesEnumTypeForEmptyEnumSet() {
        final EnumSet<Color> colors = EnumSet.noneOf(Color.class);

        final Class<? extends Enum<?>> enumType = ClassUtil.findEnumType(colors);

        assertThat(enumType).isEqualTo(Color.class);
    }

    @Test
    void locatesEnumTypeForEmptyEnumMap() {
        final EnumMap<Color, String> colors = new EnumMap<Color, String>(Color.class);

        final Class<? extends Enum<?>> enumType = ClassUtil.findEnumType(colors);

        assertThat(enumType).isEqualTo(Color.class);
    }

    private enum Color {
        RED,
        BLUE
    }
}
