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

import static org.assertj.core.api.Assertions.assertThat;

public class FloatConverterTest {
    @Test
    public void decodesListsAsWrapperFloatArrays() {
        final FloatConverter converter = new FloatConverter();

        final Object decoded = converter.decode(Float[].class, Arrays.asList(1.25F, 2.5F, 3.75F));

        assertThat(decoded).isInstanceOf(Float[].class);
        assertThat((Float[]) decoded).containsExactly(1.25F, 2.5F, 3.75F);
    }

    @Test
    public void decodesListsAsPrimitiveFloatArrays() {
        final FloatConverter converter = new FloatConverter();

        final Object decoded = converter.decode(float[].class, Arrays.asList("4.25", 5, 6.5D));

        assertThat(decoded).isInstanceOf(float[].class);
        assertThat((float[]) decoded).containsExactly(4.25F, 5.0F, 6.5F);
    }
}
