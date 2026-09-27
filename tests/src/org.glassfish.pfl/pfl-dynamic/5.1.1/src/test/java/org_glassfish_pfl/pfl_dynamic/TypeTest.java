/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.pfl.dynamic.codegen.spi.Type;
import org.junit.jupiter.api.Test;

public class TypeTest {
    @Test
    void resolvesANamedTypeWithTheCurrentClassLoader() {
        Type type = Type._class(NamedValue.class.getName());

        assertThat(type.getTypeClass()).isEqualTo(NamedValue.class);
        assertThat(type.name()).isEqualTo(NamedValue.class.getName());
    }

    public static final class NamedValue {}
}
