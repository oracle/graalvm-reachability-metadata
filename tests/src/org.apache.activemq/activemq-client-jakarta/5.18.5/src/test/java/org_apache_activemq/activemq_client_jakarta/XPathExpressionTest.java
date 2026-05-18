/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;

import jakarta.jms.JMSException;

import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.command.Message;
import org.apache.activemq.filter.BooleanExpression;
import org.apache.activemq.filter.MessageEvaluationContext;
import org.apache.activemq.filter.XPathExpression;
import org.apache.activemq.selector.SelectorParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class XPathExpressionTest {
    private static final char NESTED_CLASS_SEPARATOR = 36;
    private static final String EVALUATOR_PROPERTY = "org.apache.activemq.XPathEvaluatorClassName";
    private static final String EVALUATOR_CLASS_NAME = XPathExpressionTest.class.getName()
            + NESTED_CLASS_SEPARATOR + "RecordingXPathEvaluator";

    @BeforeAll
    static void installTestXPathEvaluator() {
        System.setProperty(EVALUATOR_PROPERTY, EVALUATOR_CLASS_NAME);
        SelectorParser.clearCache();
        RecordingXPathEvaluator.reset();
    }

    @Test
    void selectorParserCreatesAndUsesConfiguredXPathEvaluator() throws Exception {
        BooleanExpression expression = SelectorParser.parse("XPATH 'approved'");
        ActiveMQTextMessage message = new ActiveMQTextMessage();
        message.setText("<order status=\"approved\" />");

        boolean matches = expression.matches(new FixedMessageEvaluationContext(message));

        assertThat(matches).isTrue();
        assertThat(RecordingXPathEvaluator.constructorCalls).isEqualTo(1);
        assertThat(RecordingXPathEvaluator.lastXPath).isEqualTo("approved");
        assertThat(RecordingXPathEvaluator.receivedDocumentBuilder).isTrue();
    }

    public static final class RecordingXPathEvaluator implements XPathExpression.XPathEvaluator {
        private static int constructorCalls;
        private static String lastXPath;
        private static boolean receivedDocumentBuilder;

        public RecordingXPathEvaluator(String xpath, DocumentBuilder documentBuilder) {
            constructorCalls++;
            lastXPath = xpath;
            receivedDocumentBuilder = documentBuilder != null;
        }

        static void reset() {
            constructorCalls = 0;
            lastXPath = null;
            receivedDocumentBuilder = false;
        }

        @Override
        public boolean evaluate(Message message) throws JMSException {
            if (message instanceof ActiveMQTextMessage) {
                String text = ((ActiveMQTextMessage) message).getText();
                return text != null && text.contains(lastXPath);
            }
            return false;
        }
    }

    private static final class FixedMessageEvaluationContext extends MessageEvaluationContext {
        private final Message message;

        private FixedMessageEvaluationContext(Message message) {
            this.message = message;
        }

        @Override
        public boolean isDropped() {
            return false;
        }

        @Override
        public Message getMessage() throws IOException {
            return message;
        }
    }
}
