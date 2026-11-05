/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GroovyTest {
    @Test
    void modelsClassTypesAndPrimitiveWrappers() {
        ClassNode string = ClassHelper.make(String.class);
        assertEquals("java.lang.String", string.getName());
        assertTrue(string.getMethods("substring").stream().anyMatch(method -> method.getParameters().length == 2));
        assertEquals(Integer.class, ClassHelper.getWrapper(ClassHelper.int_TYPE).getTypeClass());
    }
}
