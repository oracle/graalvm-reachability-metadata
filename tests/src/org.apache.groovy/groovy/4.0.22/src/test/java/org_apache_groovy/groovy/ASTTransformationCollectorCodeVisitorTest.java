/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassNode;
import org.apache.groovy.ast.tools.AnnotatedNodeUtils;
import org.codehaus.groovy.ast.Parameter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ASTTransformationCollectorCodeVisitorTest {
    @Test
    void discoversAndAppliesAnAnnotationDeclaredTransform() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("Receipt", """
                @groovy.transform.ToString(includeNames=true)
                class Receipt { String reference }
                """);
        assertNotNull(model.getDeclaredMethod("toString", Parameter.EMPTY_ARRAY));
        assertTrue(AnnotatedNodeUtils.isGenerated(model.getDeclaredMethod("toString", Parameter.EMPTY_ARRAY)));
    }
}
