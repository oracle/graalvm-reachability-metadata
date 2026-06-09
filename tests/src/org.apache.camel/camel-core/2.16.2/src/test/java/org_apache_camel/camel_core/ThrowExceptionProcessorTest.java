/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.processor.ThrowExceptionProcessor;
import org.junit.jupiter.api.Test;

public class ThrowExceptionProcessorTest {
    @Test
    void createsConfiguredExceptionWithEvaluatedMessage() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        ThrowExceptionProcessor processor = new ThrowExceptionProcessor(
                new DynamicMessageException("template"), DynamicMessageException.class, "Hello ${body}");
        processor.setCamelContext(context);
        try {
            processor.start();

            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody("Camel");

            processor.process(exchange);

            assertThat(exchange.getException())
                    .isInstanceOf(DynamicMessageException.class)
                    .hasMessage("Hello Camel");
        } finally {
            processor.stop();
            context.stop();
        }
    }

    public static class DynamicMessageException extends Exception {
        public DynamicMessageException(String message) {
            super(message);
        }
    }
}
