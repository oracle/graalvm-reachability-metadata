/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.converters.ShortConverter;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ShortConverterTest {
    @Test
    void decodesListAsPrimitiveArray() {
        ShortConverter converter = new ShortConverter();
        List<Number> values = Arrays.asList(Short.valueOf((short) 1), Integer.valueOf(2), Long.valueOf(3L));

        Object decoded = converter.decode(short[].class, values);

        assertThat(decoded).isInstanceOf(short[].class);
        assertThat((short[]) decoded).containsExactly((short) 1, (short) 2, (short) 3);
    }

    @Test
    void decodesListAsWrapperArray() {
        ShortConverter converter = new ShortConverter();
        List<Number> values = Arrays.asList(Integer.valueOf(4), Short.valueOf((short) 5), Byte.valueOf((byte) 6));

        Object decoded = converter.decode(Short[].class, values);

        assertThat(decoded).isInstanceOf(Short[].class);
        assertThat((Short[]) decoded).containsExactly(Short.valueOf((short) 4), Short.valueOf((short) 5),
                Short.valueOf((short) 6));
    }
}
