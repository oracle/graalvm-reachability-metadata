/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import org.codehaus.commons.compiler.samples.DemoBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class DemoBaseTest {
    @Test
    void createsObjectsFromEmptyAndStringValues() throws Exception {
        Object defaultConstructedObject = DemoBase.createObject(StringBuilder.class, "");
        Object stringConstructedObject = DemoBase.createObject(BigDecimal.class, "12.50");
        Object primitiveConstructedObject = DemoBase.createObject(int.class, "42");

        assertThat(defaultConstructedObject).isInstanceOf(StringBuilder.class);
        assertThat(defaultConstructedObject.toString()).isEmpty();
        assertThat(stringConstructedObject).isEqualTo(new BigDecimal("12.50"));
        assertThat(primitiveConstructedObject).isEqualTo(42);
    }

    @Test
    void convertsTypeNamesToClasses() {
        assertThat(DemoBase.stringToType("java.lang.String")).isSameAs(String.class);
        assertThat(DemoBase.stringToType("int[]")).isSameAs(int[].class);
        assertThat(DemoBase.stringToTypes("int,java.lang.String,int[]"))
            .containsExactly(int.class, String.class, int[].class);
    }
}
