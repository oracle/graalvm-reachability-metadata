/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.component.configurator.converters.basic.ClassConverter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassConverterTest {
    @Test
    public void recognizesClassValuesOnly() {
        ClassConverter converter = new ClassConverter();

        assertTrue(converter.canConvert(Class.class));
        assertFalse(converter.canConvert(String.class));
    }

    @Test
    public void convertsFullyQualifiedClassNameToClass() throws Exception {
        ClassConverter converter = new ClassConverter();

        Object converted = converter.fromString(ArrayList.class.getName());

        assertSame(ArrayList.class, converted);
    }
}
