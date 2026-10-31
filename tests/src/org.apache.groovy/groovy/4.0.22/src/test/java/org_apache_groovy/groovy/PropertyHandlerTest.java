/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertyHandlerTest {
    @Test
    void usesAConfiguredPropertyHandlerForTupleConstruction() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("Location", """
                @groovy.transform.TupleConstructor
                @groovy.transform.PropertyOptions(propertyHandler=groovy.transform.options.DefaultPropertyHandler)
                class Location {
                    String city
                    int zone
                }
                """);
        assertTrue(model.getDeclaredConstructors().stream().anyMatch(constructor -> constructor.getParameters().length == 2));
        assertEquals("java.lang.String", model.getProperty("city").getType().getName());
    }
}
