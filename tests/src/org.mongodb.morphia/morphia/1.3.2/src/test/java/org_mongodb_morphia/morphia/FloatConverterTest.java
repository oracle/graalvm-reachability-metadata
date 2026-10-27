/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.converters.FloatConverter;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FloatConverterTest {
    @Test
    void decodesListAsPrimitiveArray() {
        FloatConverter converter = new FloatConverter();
        List<?> values = Arrays.asList(1.25D, "2.5", 3);

        Object decoded = converter.decode(float[].class, values);

        assertThat(decoded).isInstanceOf(float[].class);
        assertThat((float[]) decoded).containsExactly(1.25F, 2.5F, 3.0F);
    }
}
