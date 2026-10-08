/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.aspectj.bridge.IMessage;
import org.aspectj.bridge.IMessageHandler;
import org.aspectj.weaver.loadtime.Options;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OptionsTest {
    @BeforeEach
    void resetMessageHandlers() {
        RecordingMessageHandler.reset();
    }

    @Test
    void parseInstantiatesConfiguredMessageHandlerAndRoutesSubsequentWarnings() {
        SilentMessageHandler fallbackMessageHandler = new SilentMessageHandler();
        String options = "-XmessageHandlerClass:" + RecordingMessageHandler.class.getName() + " -unknownLtwOption";

        Options.parse(options, OptionsTest.class.getClassLoader(), fallbackMessageHandler);

        assertThat(fallbackMessageHandler.messages()).isEmpty();
        assertThat(RecordingMessageHandler.constructorCalls()).isOne();
        assertThat(RecordingMessageHandler.messages()).hasSize(1);
        IMessage warning = RecordingMessageHandler.messages().get(0);
        assertThat(warning.getKind()).isSameAs(IMessage.WARNING);
        assertThat(warning.getMessage()).contains("-unknownLtwOption", "unknown option");
    }

    public static final class RecordingMessageHandler implements IMessageHandler {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();
        private static final List<IMessage> MESSAGES = Collections.synchronizedList(new ArrayList<>());

        public RecordingMessageHandler() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        @Override
        public boolean handleMessage(IMessage message) {
            MESSAGES.add(message);
            return true;
        }

        @Override
        public boolean isIgnoring(IMessage.Kind kind) {
            return false;
        }

        @Override
        public void dontIgnore(IMessage.Kind kind) {
        }

        @Override
        public void ignore(IMessage.Kind kind) {
        }

        static int constructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }

        static List<IMessage> messages() {
            return MESSAGES;
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
            MESSAGES.clear();
        }
    }

    private static final class SilentMessageHandler implements IMessageHandler {
        private final List<IMessage> messages = new ArrayList<>();

        @Override
        public boolean handleMessage(IMessage message) {
            messages.add(message);
            return true;
        }

        @Override
        public boolean isIgnoring(IMessage.Kind kind) {
            return false;
        }

        @Override
        public void dontIgnore(IMessage.Kind kind) {
        }

        @Override
        public void ignore(IMessage.Kind kind) {
        }

        List<IMessage> messages() {
            return messages;
        }
    }
}
