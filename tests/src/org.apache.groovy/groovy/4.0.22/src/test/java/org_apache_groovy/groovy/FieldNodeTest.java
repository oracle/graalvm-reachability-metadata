/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.FieldNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FieldNodeTest {
    @Test
    void modelsAPublicStaticField() throws Exception {
        FieldNode field = FieldNode.newStatic(Integer.class, "MAX_VALUE");
        assertEquals("MAX_VALUE", field.getName());
        assertEquals(int.class, field.getType().getTypeClass());
        assertEquals(Integer.class, field.getOwner().getTypeClass());
        assertTrue(field.isStatic());
    }
}
