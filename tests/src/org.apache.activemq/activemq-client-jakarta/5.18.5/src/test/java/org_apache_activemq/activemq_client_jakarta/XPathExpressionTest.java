/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import java.io.IOException;

import jakarta.jms.JMSException;
import javax.xml.parsers.DocumentBuilder;

import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.command.Message;
import org.apache.activemq.filter.BooleanExpression;
import org.apache.activemq.filter.MessageEvaluationContext;
import org.apache.activemq.filter.XPathExpression.XPathEvaluator;
import org.apache.activemq.selector.SelectorParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XPathExpressionTest {

    private static final String EVALUATOR_SYSTEM_PROPERTY = "org.apache.activemq.XPathEvaluatorClassName";
    private static final String XPATH = "//order[@priority=\"high\"]";
    private static String previousEvaluatorClassName;

    @BeforeAll
    static void configureXPathEvaluator() {
        previousEvaluatorClassName = System.setProperty(
                EVALUATOR_SYSTEM_PROPERTY,
                RecordingXPathEvaluator.class.getName());
        SelectorParser.clearCache();
    }

    @AfterAll
    static void restoreXPathEvaluatorProperty() {
        if (previousEvaluatorClassName == null) {
            System.clearProperty(EVALUATOR_SYSTEM_PROPERTY);
        } else {
            System.setProperty(EVALUATOR_SYSTEM_PROPERTY, previousEvaluatorClassName);
        }
        SelectorParser.clearCache();
    }

    @Test
    void parseXPathSelectorCreatesConfiguredEvaluator() throws Exception {
        BooleanExpression selector = SelectorParser.parse("XPATH '" + XPATH + "'");
        ActiveMQTextMessage message = new ActiveMQTextMessage();
        message.setText("<order priority=\"high\"/>");
        MessageEvaluationContext context = new FixedMessageEvaluationContext(message);

        boolean matches = selector.matches(context);

        assertThat(matches).isTrue();
        assertThat(selector.toString()).isEqualTo("XPATH '" + XPATH + "'");
        assertThat(RecordingXPathEvaluator.lastXPath).isEqualTo(XPATH);
        assertThat(RecordingXPathEvaluator.constructorReceivedDocumentBuilder).isTrue();
    }

    public static final class RecordingXPathEvaluator implements XPathEvaluator {
        private static String lastXPath;
        private static boolean constructorReceivedDocumentBuilder;

        private final String xpath;

        public RecordingXPathEvaluator(String xpath, DocumentBuilder builder) {
            this.xpath = xpath;
            lastXPath = xpath;
            constructorReceivedDocumentBuilder = builder != null;
        }

        @Override
        public boolean evaluate(Message message) throws JMSException {
            return XPATH.equals(xpath)
                    && message instanceof ActiveMQTextMessage
                    && ((ActiveMQTextMessage) message).getText().contains("priority=\"high\"");
        }
    }

    private static final class FixedMessageEvaluationContext extends MessageEvaluationContext {
        private final Message message;

        FixedMessageEvaluationContext(Message message) {
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
