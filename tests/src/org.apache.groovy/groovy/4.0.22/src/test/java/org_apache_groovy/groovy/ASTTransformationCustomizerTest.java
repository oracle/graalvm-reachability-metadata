/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.transform.ToString;
import groovy.util.logging.Log;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.transform.LogASTTransformation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ASTTransformationCustomizerTest {
    @Test
    void appliesAnAnnotationTransformationFromCompilerConfiguration() throws Exception {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(new ASTTransformationCustomizer(Map.of("includeNames", true), ToString.class));
        ClassNode model = AstCompilationSupport.canonicalize("ShipmentLabel", "class ShipmentLabel { String destination }", configuration);
        assertNotNull(model.getDeclaredMethod("toString", Parameter.EMPTY_ARRAY));
    }

    @Test
    void loadsAnExplicitTransformationByName() throws Exception {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(new ASTTransformationCustomizer(Map.of("value", "audit"),
                Log.class, LogASTTransformation.class.getName()));
        ClassNode model = AstCompilationSupport.canonicalize("ShipmentAudit", "class ShipmentAudit {}", configuration);
        assertNotNull(model.getDeclaredField("audit"));
        assertEquals("java.util.logging.Logger", model.getDeclaredField("audit").getType().getName());
    }
}
