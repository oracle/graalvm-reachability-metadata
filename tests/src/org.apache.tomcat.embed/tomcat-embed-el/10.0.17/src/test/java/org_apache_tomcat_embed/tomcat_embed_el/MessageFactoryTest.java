/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ExpressionFactory;

import org.apache.el.ExpressionFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class MessageFactoryTest {

    @Test
    void rejectsNullExpectedTypeWithLocalizedMessage() {
        ExpressionFactory expressionFactory = new ExpressionFactoryImpl();

        assertThatNullPointerException()
                .isThrownBy(() -> expressionFactory.createValueExpression("value", null))
                .withMessage("Expected type cannot be null");
    }
}
