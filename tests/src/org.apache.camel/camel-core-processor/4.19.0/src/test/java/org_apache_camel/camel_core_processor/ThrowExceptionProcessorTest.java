/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core_processor;

import java.io.Serial;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.ThrowExceptionProcessor;
import org.apache.camel.processor.resequencer.MessageRejectedException;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThrowExceptionProcessorTest {
    @Test
    void createsConfiguredExceptionWithMessage() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            ThrowExceptionProcessor processor = new ThrowExceptionProcessor(
                    null, MessageRejectedException.class, "Rejected payload");
            processor.setCamelContext(context);
            processor.init();

            Exchange exchange = new DefaultExchange(context);

            boolean completedSynchronously = process(processor, exchange);

            assertThat(completedSynchronously).isTrue();
            assertThat(exchange.getException())
                    .isInstanceOf(MessageRejectedException.class)
                    .hasMessage("Rejected payload");
        }
    }

    @Test
    void createsConfiguredExceptionWithDefaultConstructor() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            ThrowExceptionProcessor processor = new ThrowExceptionProcessor(
                    null, DefaultConstructedException.class, null);

            boolean completedSynchronously = process(processor, exchange);

            assertThat(completedSynchronously).isTrue();
            assertThat(exchange.getException()).isInstanceOf(DefaultConstructedException.class);
        }
    }

    private static boolean process(ThrowExceptionProcessor processor, Exchange exchange) {
        AtomicBoolean callbackDoneSynchronously = new AtomicBoolean();
        boolean completedSynchronously = processor.process(exchange, callbackDoneSynchronously::set);
        assertThat(callbackDoneSynchronously.get()).isTrue();
        return completedSynchronously;
    }

    public static final class DefaultConstructedException extends Exception {
        private static final @Serial long serialVersionUID = 1L;

        public DefaultConstructedException() {
        }
    }
}
