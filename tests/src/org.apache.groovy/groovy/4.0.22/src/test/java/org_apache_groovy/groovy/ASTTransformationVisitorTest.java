/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.Parameter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ASTTransformationVisitorTest {
    @Test
    void instantiatesAndRunsTheEqualityTransformAtItsCompilationPhase() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("Address", """
                @groovy.transform.EqualsAndHashCode
                class Address { String street }
                """);
        assertNotNull(model.getDeclaredMethod("hashCode", Parameter.EMPTY_ARRAY));
        assertFalse(model.getMethods("equals").isEmpty());
        assertFalse(model.getMethods("canEqual").isEmpty());
    }
}
