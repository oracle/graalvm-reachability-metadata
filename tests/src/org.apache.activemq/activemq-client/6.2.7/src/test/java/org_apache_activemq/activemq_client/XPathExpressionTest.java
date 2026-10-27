/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.filter.BooleanExpression;
import org.apache.activemq.filter.MessageEvaluationContext;
import org.apache.activemq.selector.SelectorParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XPathExpressionTest {

    @Test
    void evaluatesAnXpathSelectorAgainstXmlMessageContent() throws Exception {
        BooleanExpression selector = SelectorParser.parse("XPATH '/order[item=\"coffee\"]'");
        ActiveMQTextMessage message = new ActiveMQTextMessage();
        message.setText("<order><item>coffee</item></order>");
        MessageEvaluationContext context = new MessageEvaluationContext();
        context.setMessageReference(message);

        assertThat(selector.matches(context)).isTrue();
    }
}
