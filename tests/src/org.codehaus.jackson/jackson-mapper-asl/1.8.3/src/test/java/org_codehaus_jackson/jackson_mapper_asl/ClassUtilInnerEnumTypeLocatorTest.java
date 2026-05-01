/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import java.util.EnumMap;
import java.util.EnumSet;

import org.codehaus.jackson.map.util.ClassUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilInnerEnumTypeLocatorTest {
    @Test
    void locatesElementTypeForEmptyEnumSet() {
        EnumSet<Signal> values = EnumSet.noneOf(Signal.class);

        Class<? extends Enum<?>> enumType = ClassUtil.findEnumType(values);

        assertThat(enumType).isSameAs(Signal.class);
        assertThat(values).isEmpty();
    }

    @Test
    void locatesKeyTypeForEmptyEnumMap() {
        EnumMap<Signal, String> values = new EnumMap<>(Signal.class);

        Class<? extends Enum<?>> enumType = ClassUtil.findEnumType(values);

        assertThat(enumType).isSameAs(Signal.class);
        assertThat(values).isEmpty();
    }

    private enum Signal {
        STARTED,
        STOPPED
    }
}
