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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class StaticTypeCheckingVisitorTest {
    @Test
    void infersClosureParametersFromThePublicCollectSignature() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("Names", """
                @groovy.transform.TypeChecked
                class Names {
                    static List<String> uppercase() {
                        ['ada', 'lin'].collect { it.toUpperCase() }
                    }
                }
                """);
        assertNotNull(model.getDeclaredMethod("uppercase", Parameter.EMPTY_ARRAY));
        assertEquals("java.util.List", model.getDeclaredMethod("uppercase", Parameter.EMPTY_ARRAY).getReturnType().getName());
    }
}
