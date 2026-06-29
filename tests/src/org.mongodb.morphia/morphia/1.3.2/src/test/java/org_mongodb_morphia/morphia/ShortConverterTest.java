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

import static org.assertj.core.api.Assertions.assertThat;

public class ShortConverterTest {
    @Test
    public void decodesListsAsWrapperShortArrays() {
        final ShortConverter converter = new ShortConverter();

        final Object decoded = converter.decode(Short[].class, Arrays.asList(1, 2L, (short) 3));

        assertThat(decoded).isInstanceOf(Short[].class);
        assertThat((Short[]) decoded).containsExactly((short) 1, (short) 2, (short) 3);
    }

    @Test
    public void decodesListsAsPrimitiveShortArrays() {
        final ShortConverter converter = new ShortConverter();

        final Object decoded = converter.decode(short[].class, Arrays.asList(4, 5L, (short) 6));

        assertThat(decoded).isInstanceOf(short[].class);
        assertThat((short[]) decoded).containsExactly((short) 4, (short) 5, (short) 6);
    }
}
