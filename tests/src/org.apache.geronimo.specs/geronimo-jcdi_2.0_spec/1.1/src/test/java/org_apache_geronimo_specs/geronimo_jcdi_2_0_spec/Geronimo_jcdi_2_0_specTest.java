/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_jcdi_2_0_spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import javax.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.Test;

public class Geronimo_jcdi_2_0_specTest {
    @Test
    void capturesParameterizedType() {
        TypeLiteral<List<String>> literal = new TypeLiteral<List<String>>() { };

        assertThat(literal.getRawType()).isEqualTo(List.class);
        assertThat(literal.getType()).isInstanceOf(ParameterizedType.class);
        ParameterizedType type = (ParameterizedType) literal.getType();
        assertThat(type.getRawType()).isEqualTo(List.class);
        assertThat(type.getActualTypeArguments()).containsExactly(String.class);
    }
}
