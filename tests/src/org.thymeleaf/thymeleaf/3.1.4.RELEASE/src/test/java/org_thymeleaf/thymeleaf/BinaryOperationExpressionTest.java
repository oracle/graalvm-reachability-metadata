/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.ExpressionContext;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryOperationExpressionTest {

    @Test
    void parsesAndExecutesAdditionBetweenVariableExpressions() {
        TemplateEngine templateEngine = new TemplateEngine();
        ExpressionContext context = new ExpressionContext(
                templateEngine.getConfiguration(),
                Locale.US,
                Map.of("left", 3, "right", 4));
        IStandardExpressionParser expressionParser = StandardExpressions.getExpressionParser(
                templateEngine.getConfiguration());

        Object result = expressionParser.parseExpression(context, "${left} + ${right}").execute(context);

        assertThat(result.toString()).isEqualTo("7");
    }
}
