/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import org.codehaus.commons.compiler.samples.DemoBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DemoBaseTest {
    @Test
    void createsObjectsUsingDefaultAndStringConstructors() throws Exception {
        Object defaultConstructed = DemoBase.createObject(StringBuilder.class, "");
        Object stringConstructed = DemoBase.createObject(String.class, "janino");
        Object primitiveWrapper = DemoBase.createObject(int.class, "42");

        assertThat(defaultConstructed).isInstanceOf(StringBuilder.class);
        assertThat(stringConstructed).isEqualTo("janino");
        assertThat(primitiveWrapper).isEqualTo(42);
    }

    @Test
    void convertsNamesToPrimitiveReferenceAndArrayTypes() {
        assertThat(DemoBase.stringToType("int")).isEqualTo(int.class);
        assertThat(DemoBase.stringToType("java.lang.String")).isEqualTo(String.class);
        assertThat(DemoBase.stringToType("int[]")).isEqualTo(int[].class);
        assertThat(DemoBase.stringToTypes("int,java.lang.String"))
            .containsExactly(int.class, String.class);
    }
}
