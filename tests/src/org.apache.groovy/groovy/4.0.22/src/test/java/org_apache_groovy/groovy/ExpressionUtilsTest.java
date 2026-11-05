/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.apache.groovy.ast.tools.ExpressionUtils;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ExpressionUtilsTest {
    @Test
    void inlinesAPublicStaticConstantIntoAnAstExpression() {
        PropertyExpression property = new PropertyExpression(new ClassExpression(ClassHelper.make(Integer.class)), "MAX_VALUE");
        Expression result = ExpressionUtils.transformInlineConstants(property, ClassHelper.int_TYPE);
        ConstantExpression constant = assertInstanceOf(ConstantExpression.class, result);
        assertEquals(Integer.MAX_VALUE, constant.getValue());
    }
}
