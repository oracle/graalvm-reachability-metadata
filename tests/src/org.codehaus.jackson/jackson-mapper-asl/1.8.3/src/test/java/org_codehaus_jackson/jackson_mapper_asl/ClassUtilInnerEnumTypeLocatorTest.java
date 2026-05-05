/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.EnumSet;

import org.codehaus.jackson.map.util.ClassUtil;
import org.junit.jupiter.api.Test;

public class ClassUtilInnerEnumTypeLocatorTest {
    @Test
    public void locatesEnumTypeForEmptyEnumSet() {
        EnumSet<Color> colors = EnumSet.noneOf(Color.class);

        Class<? extends Enum<?>> enumType = ClassUtil.findEnumType(colors);

        assertThat(enumType).isEqualTo(Color.class);
    }

    @Test
    public void locatesEnumTypeForEmptyEnumMap() {
        EnumMap<Color, String> colors = new EnumMap<Color, String>(Color.class);

        Class<? extends Enum<?>> enumType = ClassUtil.findEnumType(colors);

        assertThat(enumType).isEqualTo(Color.class);
    }

    private enum Color {
        RED,
        BLUE
    }
}
