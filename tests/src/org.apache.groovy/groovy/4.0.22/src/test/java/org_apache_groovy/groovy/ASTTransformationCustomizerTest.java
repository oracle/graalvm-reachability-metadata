/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.transform.ToString;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ASTTransformationCustomizerTest {
    @Test
    void appliesAConfiguredTransformWithoutASourceAnnotation() throws Exception {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(new ASTTransformationCustomizer(ToString.class));
        ClassNode model = AstCompilationSupport.canonicalize("Manifest", "class Manifest { String name }", configuration);
        assertNotNull(model.getDeclaredMethod("toString", Parameter.EMPTY_ARRAY));
    }
}
