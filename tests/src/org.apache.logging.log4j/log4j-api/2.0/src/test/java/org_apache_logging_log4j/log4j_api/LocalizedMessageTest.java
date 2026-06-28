/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ListResourceBundle;
import java.util.Locale;

import org.apache.logging.log4j.message.LocalizedMessage;
import org.junit.jupiter.api.Test;

public class LocalizedMessageTest {
    @Test
    void formatsMessageFromBaseNameBundleWithDefaultLocale() {
        LocalizedMessage message = new LocalizedMessage(Messages.class.getName(), "defaultGreeting", "Ada");

        assertThat(message.getFormattedMessage()).isEqualTo("Hello Ada");
    }

    @Test
    void formatsMessageFromBaseNameBundleWithExplicitLocale() {
        LocalizedMessage message = new LocalizedMessage(
                Messages.class.getName(), Locale.US, "localizedGreeting", "Grace");

        assertThat(message.getFormattedMessage()).isEqualTo("Howdy Grace");
    }

    @Test
    void walksLoggerNameHierarchyToFindDefaultLocaleBundle() {
        LocalizedMessage message = new LocalizedMessage("loopGreeting", "Alan");
        message.setLoggerName(LoggerMessages.class.getName() + ".child.Component");

        assertThat(message.getFormattedMessage()).isEqualTo("Logged Alan");
    }

    @Test
    void walksLoggerNameHierarchyToFindExplicitLocaleBundle() {
        LocalizedMessage message = new LocalizedMessage(Locale.US, "loopLocalizedGreeting", "Barbara");
        message.setLoggerName(LoggerMessages.class.getName() + ".child.Component");

        assertThat(message.getFormattedMessage()).isEqualTo("Logged in the US Barbara");
    }

    @Test
    void serializesAndDeserializesLocalizedMessage() throws Exception {
        LocalizedMessage original = new LocalizedMessage(Messages.class.getName(), "defaultGreeting", "Katherine");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        LocalizedMessage restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (LocalizedMessage) input.readObject();
        }

        assertThat(restored.getFormattedMessage()).isEqualTo("Hello Katherine");
        assertThat(restored.getFormat()).isEqualTo("defaultGreeting");
        assertThat(restored.getParameters()).containsExactly("Katherine");
    }

    public static class Messages extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                {"defaultGreeting", "Hello {}"}
            };
        }
    }

    public static class Messages_en_US extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                {"localizedGreeting", "Howdy {}"}
            };
        }
    }

    public static class LoggerMessages extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                {"loopGreeting", "Logged {}"}
            };
        }
    }

    public static class LoggerMessages_en_US extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                {"loopLocalizedGreeting", "Logged in the US {}"}
            };
        }
    }
}
