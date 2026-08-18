/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.TypeLiteral;
import com.google.inject.util.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MoreTypesTest {
    @Test
    void typeLiteralResolvesRawTypeForGenericArrays() {
        TypeLiteral<?> literal = TypeLiteral.get(Types.arrayOf(Types.listOf(String.class)));

        assertThat(literal.getRawType()).isEqualTo(List[].class);
        assertThat(literal.toString()).isEqualTo("java.util.List<java.lang.String>[]");
    }
}
