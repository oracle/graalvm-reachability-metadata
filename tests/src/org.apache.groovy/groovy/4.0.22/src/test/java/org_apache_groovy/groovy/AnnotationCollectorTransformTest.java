/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.Parameter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationCollectorTransformTest {
    @Test
    void expandsThePrecompiledCanonicalAnnotation() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("Address", """
                @groovy.transform.Canonical
                class Address { String city; String street }
                """);
        assertNotNull(model.getDeclaredMethod("toString", Parameter.EMPTY_ARRAY));
        assertNotNull(model.getDeclaredMethod("hashCode", Parameter.EMPTY_ARRAY));
        assertNotNull(model.getDeclaredMethod("equals", new Parameter[]{new Parameter(ClassHelper.OBJECT_TYPE, "other")}));
        assertTrue(model.getDeclaredConstructors().stream().anyMatch(constructor -> constructor.getParameters().length == 2));
    }
}
