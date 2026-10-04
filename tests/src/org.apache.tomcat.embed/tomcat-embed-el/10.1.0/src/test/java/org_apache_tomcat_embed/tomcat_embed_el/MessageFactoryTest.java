/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ELException;
import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MessageFactoryTest {

    @Test
    void reportsLocalizedMessageForMalformedExpression() {
        ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(expressionFactory);

        assertThatThrownBy(() -> expressionFactory.createValueExpression(context, "${", Object.class))
                .isInstanceOf(ELException.class)
                .hasMessageContaining("Failed to parse the expression [${]");
    }
}
