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
import java.util.Locale;

import org.apache.logging.log4j.message.LocalizedMessage;
import org.junit.jupiter.api.Test;

public class LocalizedMessageTest {
    @Test
    void formatsMessageFromExactBaseNameBundle() {
        LocalizedMessage message = new LocalizedMessage(
            "localizedmessageExact",
            "greeting",
            new Object[] {"Ada"});

        assertThat(message.getFormattedMessage()).isEqualTo("Hello Ada");
    }

    @Test
    void formatsMessageFromExactBaseNameBundleWithLocale() {
        LocalizedMessage message = new LocalizedMessage(
            "localizedmessageExact",
            Locale.FRENCH,
            "greeting",
            new Object[] {"Ada"});

        assertThat(message.getFormattedMessage()).isEqualTo("Bonjour Ada");
    }

    @Test
    void walksLoggerNameParentsUntilBundleIsFound() {
        LocalizedMessage message = new LocalizedMessage(
            "loggerGreeting",
            new Object[] {"Grace"});
        message.setLoggerName("org_apache_logging_log4j.log4j_api.bundle.child.Logger");

        assertThat(message.getFormattedMessage()).isEqualTo("Logger hello Grace");
    }

    @Test
    void walksLoggerNameParentsUntilLocalizedBundleIsFound() {
        LocalizedMessage message = new LocalizedMessage(
            Locale.GERMAN,
            "loggerGreeting",
            new Object[] {"Grace"});
        message.setLoggerName("org_apache_logging_log4j.log4j_api.localized.child.Logger");

        assertThat(message.getFormattedMessage()).isEqualTo("Logger hallo Grace");
    }

    @Test
    void serializesAndDeserializesLocalizedMessage() throws Exception {
        LocalizedMessage original = new LocalizedMessage(
            "localizedmessageSerialization",
            "serialized",
            new Object[] {"Katherine"});

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        LocalizedMessage restored;
        try (ObjectInputStream input = new ObjectInputStream(
            new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (LocalizedMessage) input.readObject();
        }

        assertThat(restored.getFormattedMessage()).isEqualTo("Serialized Katherine");
        assertThat(restored.getParameters()).containsExactly("Katherine");
    }
}
