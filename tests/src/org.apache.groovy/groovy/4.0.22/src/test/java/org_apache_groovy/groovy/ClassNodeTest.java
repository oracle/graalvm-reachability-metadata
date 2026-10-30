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

public class ClassNodeTest {
    @Test
    void modelsResolvedAndSymbolicArrayTypes() {
        ClassNode array = ClassHelper.make(String.class).makeArray();
        assertTrue(array.isArray());
        assertEquals(String[].class, array.getTypeClass());
        assertEquals(String.class, array.getComponentType().getTypeClass());
        ClassNode symbolic = ClassHelper.makeWithoutCaching("java.lang.Integer").makeArray();
        symbolic.getComponentType().setRedirect(ClassHelper.make(Integer.class));
        assertEquals(Integer[].class, symbolic.getTypeClass());
    }
}
