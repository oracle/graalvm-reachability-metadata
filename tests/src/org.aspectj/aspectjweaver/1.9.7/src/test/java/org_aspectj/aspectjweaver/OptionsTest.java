/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.bridge.IMessage;
import org.aspectj.bridge.IMessageHandler;
import org.aspectj.bridge.MessageHandler;
import org.aspectj.weaver.loadtime.Options;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class OptionsTest {
    @Test
    void parseInstantiatesConfiguredMessageHandlerBeforeHandlingRemainingOptions() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String options = "-XmessageHandlerClass:" + MessageHandler.class.getName() + " -definitelyUnknownOption";

        assertThatNoException().isThrownBy(() -> Options.parse(options, classLoader, IMessageHandler.THROW));
    }

    @Test
    void parseReportsInvalidConfiguredMessageHandlerThroughOriginalHandler() {
        MessageHandler fallbackHandler = new MessageHandler();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String missingHandlerName = MessageHandler.class.getName() + "Missing";

        Options.parse("-XmessageHandlerClass:" + missingHandlerName, classLoader, fallbackHandler);

        IMessage[] errors = fallbackHandler.getErrors();
        assertThat(errors).hasSize(1);
        assertThat(errors[0].getMessage())
                .contains("Cannot instantiate message handler")
                .contains(missingHandlerName);
    }
}
