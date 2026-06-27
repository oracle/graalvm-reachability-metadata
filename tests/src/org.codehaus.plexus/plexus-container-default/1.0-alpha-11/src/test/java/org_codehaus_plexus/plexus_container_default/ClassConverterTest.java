/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_container_default;

import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.basic.ClassConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassConverterTest {
    @Test
    public void convertsClassNameToClassInstance() throws Exception {
        ClassConverter converter = new ClassConverter();

        Object converted = converter.fromString(ClassConverterTest.class.getName());

        assertSame(ClassConverterTest.class, converted);
        assertTrue(converter.canConvert(Class.class));
    }

    @Test
    public void reportsMissingClassNamesAsConfigurationErrors() {
        ClassConverter converter = new ClassConverter();

        ComponentConfigurationException exception = assertThrows(
            ComponentConfigurationException.class,
            () -> converter.fromString("org.example.DoesNotExist")
        );

        assertTrue(exception.getMessage().contains("Unable to find class in conversion"));
    }
}
