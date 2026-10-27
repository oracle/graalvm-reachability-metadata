/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_leangen_geantyref.geantyref;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;

import io.leangen.geantyref.TypeFactory;
import org.junit.jupiter.api.Test;

public class GenericArrayTypeImplTest {
    @Test
    void createsArrayClassForClassComponentType() {
        Type arrayType = TypeFactory.arrayOf(String.class);

        assertThat(arrayType).isEqualTo(String[].class);
    }
}
