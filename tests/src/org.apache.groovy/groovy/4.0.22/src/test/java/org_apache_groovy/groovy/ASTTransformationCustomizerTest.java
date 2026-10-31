/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.transform.EqualsAndHashCodeASTTransformation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ASTTransformationCustomizerTest {
    @Test
    void appliesAnAnnotationTransformationWithoutAnnotatingTheSource() throws Exception {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(new ASTTransformationCustomizer(ToString.class));
        ClassNode model = AstCompilationSupport.canonicalize("ParcelLabel", """
                class ParcelLabel { String destination }
                """, configuration);
        assertNotNull(model.getDeclaredMethod("toString", Parameter.EMPTY_ARRAY));
    }

    @Test
    void loadsAnExplicitlyNamedTransformationAndAddsEqualityMethods() throws Exception {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(new ASTTransformationCustomizer(
                EqualsAndHashCode.class, EqualsAndHashCodeASTTransformation.class.getName()));
        ClassNode model = AstCompilationSupport.canonicalize("ParcelIdentity", """
                class ParcelIdentity { String reference }
                """, configuration);
        assertNotNull(model.getDeclaredMethod("hashCode", Parameter.EMPTY_ARRAY));
        assertNotNull(model.getDeclaredMethod("equals", new Parameter[]{
                new Parameter(ClassHelper.OBJECT_TYPE, "other")
        }));
    }
}
