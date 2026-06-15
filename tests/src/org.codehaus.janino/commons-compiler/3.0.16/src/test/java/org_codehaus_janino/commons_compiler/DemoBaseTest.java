/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.samples.DemoBase;
import org.junit.jupiter.api.Test;

public class DemoBaseTest {
    @Test
    public void createsObjectsWithDefaultAndStringConstructors() throws Exception {
        Object defaultConstructedObject = DemoBase.createObject(StringBuilder.class, "");
        Object stringConstructedObject = DemoBase.createObject(StringBuilder.class, "janino");

        assertThat(defaultConstructedObject).isInstanceOf(StringBuilder.class);
        assertThat(defaultConstructedObject.toString()).isEmpty();
        assertThat(stringConstructedObject).isInstanceOf(StringBuilder.class);
        assertThat(stringConstructedObject.toString()).isEqualTo("janino");
    }

    @Test
    public void resolvesPrimitiveAndReferenceTypeNames() {
        Class<?> primitiveArrayType = DemoBase.stringToType("int[]");
        Class<?> referenceType = DemoBase.stringToType("org.codehaus.commons.compiler.Location");

        assertThat(primitiveArrayType).isEqualTo(int[].class);
        assertThat(referenceType).isEqualTo(Location.class);
    }
}
