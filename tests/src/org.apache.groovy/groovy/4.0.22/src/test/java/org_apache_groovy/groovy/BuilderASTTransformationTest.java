/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuilderASTTransformationTest {
    @Test
    void appliesTheConfiguredBuilderStrategyToTheAst() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("Delivery", """
                @groovy.transform.builder.Builder(builderStrategy=groovy.transform.builder.SimpleStrategy, prefix='with')
                class Delivery {
                    String destination
                    int parcels
                }
                """);
        assertFalse(model.getMethods("withDestination").isEmpty());
        assertFalse(model.getMethods("withParcels").isEmpty());
        assertTrue(model.getMethods("withDestination").stream()
                .allMatch(method -> method.getReturnType().getName().equals("Delivery")));
    }
}
