/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import java.util.Locale;

import org.apache.logging.log4j.message.LocalizedMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizedMessageTest {

    private static final String BUNDLE = "localization.Orders";

    @Test
    void looksUpNamedBundleWithExplicitLocale() {
        LocalizedMessage message = new LocalizedMessage(BUNDLE, Locale.GERMAN, "shipped", new Object[]{"o-31"});

        assertThat(message.getFormattedMessage()).isEqualTo("Bestellung o-31 versandt");
        assertThat(message.getFormat()).isEqualTo("shipped");
        assertThat(message.getParameters()).containsExactly("o-31");
    }

    @Test
    void looksUpNamedBundleWithDefaultLocale() {
        LocalizedMessage message = new LocalizedMessage(BUNDLE, "received", new Object[]{"o-31"});

        assertThat(message.getFormattedMessage()).isEqualTo("Received o-31");
    }

    @Test
    void findsBundleFromParentLoggerNameWithExplicitLocale() {
        LocalizedMessage message = new LocalizedMessage(Locale.GERMAN, "shipped", new Object[]{"o-32"});
        message.setLoggerName(BUNDLE + ".Checkout");

        assertThat(message.getFormattedMessage()).isEqualTo("Bestellung o-32 versandt");
        assertThat(message.getLoggerName()).isEqualTo(BUNDLE + ".Checkout");
    }

    @Test
    void findsBundleFromParentLoggerNameWithDefaultLocale() {
        LocalizedMessage message = new LocalizedMessage("received", new Object[]{"o-32"});
        message.setLoggerName(BUNDLE + ".Checkout");

        assertThat(message.getFormattedMessage()).isEqualTo("Received o-32");
    }

    @Test
    void retainsLocalizedTextAndStringParametersAfterSerialization() throws Exception {
        LocalizedMessage original = new LocalizedMessage(BUNDLE, Locale.GERMAN, "shipped", new Object[]{"o-33"});
        original.setLoggerName("orders");

        LocalizedMessage copy = SerializationSupport.roundTrip(original, LocalizedMessage.class);

        assertThat(copy.getFormattedMessage()).isEqualTo("Bestellung o-33 versandt");
        assertThat(copy.getFormat()).isEqualTo("shipped");
        assertThat(copy.getParameters()).containsExactly("o-33");
        assertThat(copy.getLoggerName()).isEqualTo("orders");
        assertThat(copy.getThrowable()).isNull();
    }
}
