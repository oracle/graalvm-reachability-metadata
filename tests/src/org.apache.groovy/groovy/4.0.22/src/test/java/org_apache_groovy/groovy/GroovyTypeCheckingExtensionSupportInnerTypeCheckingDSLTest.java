/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport.TypeCheckingDSL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GroovyTypeCheckingExtensionSupportInnerTypeCheckingDSLTest {
    @Test
    void classifiesAstExpressionsThroughTheTypeCheckingDsl() {
        TypeCheckingDSL script = new PredicateScript();
        ConstantExpression constant = new ConstantExpression("parcel");
        VariableExpression variable = new VariableExpression("destination");
        assertEquals(Boolean.TRUE, script.invokeMethod("isConstantExpression", new Object[]{constant}));
        assertEquals(Boolean.FALSE, script.invokeMethod("isConstantExpression", new Object[]{variable}));
        assertEquals(Boolean.TRUE, script.invokeMethod("isVariableExpression", new Object[]{variable}));
        assertEquals(Boolean.FALSE, script.invokeMethod("isVariableExpression", new Object[]{null}));
        assertEquals(Boolean.FALSE, script.invokeMethod("isUnknownExpression", new Object[]{constant}));
    }

    public static class PredicateScript extends TypeCheckingDSL {
        @Override
        public Object run() {
            return null;
        }
    }
}
