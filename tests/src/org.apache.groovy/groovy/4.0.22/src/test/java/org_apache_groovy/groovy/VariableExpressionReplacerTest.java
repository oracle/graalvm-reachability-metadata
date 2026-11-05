/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.transform.tailrec.VariableExpressionReplacer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VariableExpressionReplacerTest {
    @Test
    void replacesAReturnedVariableAndPreservesItsSourcePosition() {
        VariableExpression original = new VariableExpression("argument");
        original.setLineNumber(7);
        ReturnStatement statement = new ReturnStatement(original);
        Closure<Boolean> predicate = new Closure<Boolean>(this) {
            @Override
            public Boolean call(Object argument) {
                return "argument".equals(((VariableExpression) argument).getName());
            }
        };
        Closure<VariableExpression> replacement = new Closure<VariableExpression>(this) {
            @Override
            public VariableExpression call(Object argument) {
                return new VariableExpression("iterationValue");
            }
        };
        new VariableExpressionReplacer(predicate, replacement).replaceIn(statement);
        assertEquals("iterationValue", statement.getExpression().getText());
        assertEquals(7, statement.getExpression().getLineNumber());
    }
}
