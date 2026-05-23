/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mongodb.morphia.converters.FloatConverter;

public class FloatConverterTest {
    @Test
    void decodesListAsWrapperFloatArray() {
        FloatConverter converter = new FloatConverter();
        List<Float> values = Arrays.asList(1.25F, 2.5F, -3.75F);

        Object decoded = converter.decode(Float[].class, values);

        assertThat(decoded).isInstanceOf(Float[].class);
        assertThat((Float[]) decoded).containsExactly(1.25F, 2.5F, -3.75F);
    }

    @Test
    void decodesListAsPrimitiveFloatArray() {
        FloatConverter converter = new FloatConverter();
        List<Number> values = Arrays.asList(4, 5.5D, -6.25F);

        Object decoded = converter.decode(float[].class, values);

        assertThat(decoded).isInstanceOf(float[].class);
        assertThat((float[]) decoded).containsExactly(4.0F, 5.5F, -6.25F);
    }
}
