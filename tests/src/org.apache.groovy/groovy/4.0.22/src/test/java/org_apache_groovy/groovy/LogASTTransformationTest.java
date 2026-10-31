/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogASTTransformationTest {
    @Test
    void addsTheNamedJavaLoggingField() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("DispatchLog", """
                @groovy.util.logging.Log(value='audit', category='delivery.audit')
                class DispatchLog {
                    void record() { audit.info('dispatched') }
                }
                """);
        FieldNode logger = model.getDeclaredField("audit");
        assertNotNull(logger);
        assertEquals("java.util.logging.Logger", logger.getType().getName());
        assertTrue(logger.isStatic());
        assertTrue(logger.isFinal());
        assertTrue(logger.getInitialExpression().getText().contains("delivery.audit"));
    }
}
