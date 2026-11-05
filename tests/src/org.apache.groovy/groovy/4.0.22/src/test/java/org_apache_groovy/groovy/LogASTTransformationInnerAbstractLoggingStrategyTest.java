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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogASTTransformationInnerAbstractLoggingStrategyTest {
    @Test
    void resolvesTheLoggingTypeWhenAddingAField() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("AuditRecord", """
                @groovy.util.logging.Slf4j(category='orders.audit')
                class AuditRecord {}
                """);
        FieldNode logger = model.getDeclaredField("log");
        assertEquals("org.slf4j.Logger", logger.getType().getName());
        assertTrue(logger.getInitialExpression().getText().contains("orders.audit"));
    }
}
