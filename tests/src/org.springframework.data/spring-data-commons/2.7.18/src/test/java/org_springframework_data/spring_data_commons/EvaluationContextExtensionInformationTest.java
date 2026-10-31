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
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public class EvaluationContextExtensionInformationTest {

    @Test
    void discoversStaticExtensionFieldsForQueryExpressions() {
        ExtensionAwareEvaluationContextProvider provider = new ExtensionAwareEvaluationContextProvider(
                List.of(new PricingExtension()));
        EvaluationContext context = provider.getEvaluationContext(null);
        SpelExpressionParser parser = new SpelExpressionParser();

        assertThat(parser.parseExpression("pricing.CURRENCY").getValue(context, String.class)).isEqualTo("EUR");
        assertThat(parser.parseExpression("TAX_RATE * 10").getValue(context, Integer.class)).isEqualTo(20);
    }

    public static class PricingExtension implements EvaluationContextExtension {

        public static final String CURRENCY = "EUR";
        public static final int TAX_RATE = 2;

        @Override
        public String getExtensionId() {
            return "pricing";
        }
    }
}
