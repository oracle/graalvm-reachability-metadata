/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.expression.IExpressionObjects;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.StandardExpressionParser;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryOperationExpressionTest {

    @Test
    void parseNestedBinaryOperations() {
        TemplateEngine templateEngine = new TemplateEngine();
        StandardExpressionParser parser = new StandardExpressionParser();
        IExpressionContext context = new ParserExpressionContext(templateEngine.getConfiguration());

        IStandardExpression arithmetic = parser.parseExpression(context, "${left} + ${right}");
        IStandardExpression equality = parser.parseExpression(context, "(${left} + ${right}) eq 5");
        IStandardExpression logical = parser.parseExpression(
                context,
                "(${left} * ${right}) gt 5 and (${right} - ${left}) eq 1");

        assertThat(arithmetic).isNotNull();
        assertThat(equality).isNotNull();
        assertThat(logical).isNotNull();
    }

    private static final class ParserExpressionContext implements IExpressionContext {

        private final IEngineConfiguration configuration;

        private ParserExpressionContext(final IEngineConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public IEngineConfiguration getConfiguration() {
            return this.configuration;
        }

        @Override
        public IExpressionObjects getExpressionObjects() {
            return null;
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public boolean containsVariable(final String name) {
            return false;
        }

        @Override
        public Set<String> getVariableNames() {
            return Collections.emptySet();
        }

        @Override
        public Object getVariable(final String name) {
            return null;
        }
    }
}
