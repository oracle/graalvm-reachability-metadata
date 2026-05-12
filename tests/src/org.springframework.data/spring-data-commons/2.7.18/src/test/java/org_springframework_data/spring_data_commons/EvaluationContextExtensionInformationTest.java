/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.spel.ExtensionAwareEvaluationContextProvider;
import org.springframework.data.spel.spi.EvaluationContextExtension;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

public class EvaluationContextExtensionInformationTest {

    @Test
    void exposesPublicStaticFieldsDeclaredOnEvaluationContextExtension() {
        ExtensionAwareEvaluationContextProvider provider = new ExtensionAwareEvaluationContextProvider(
                List.of(new StaticFieldExtension()));
        StandardEvaluationContext context = provider.getEvaluationContext(null);
        ExpressionParser parser = new SpelExpressionParser();

        Expression expression = parser.parseExpression("EXTENSION_VALUE");

        assertThat(expression.getValue(context)).isEqualTo("from-static-field");
    }

    public static class StaticFieldExtension implements EvaluationContextExtension {

        public static final String EXTENSION_VALUE = "from-static-field";

        @Override
        public String getExtensionId() {
            return "staticField";
        }
    }
}
