/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.TimeZone;

import org.bouncycastle.i18n.LocalizedMessage;
import org.junit.jupiter.api.Test;

public class LocalizedMessageTest {
    private static final String MESSAGE_RESOURCE = "org_bouncycastle.bcprov_jdk15to18."
        + "LocalizedMessageTestMessages";
    private static final Locale LOCALE = Locale.ROOT;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    void getEntryLoadsResourceBundleWithDefaultClassLoader() throws Exception {
        LocalizedMessage message = new LocalizedMessage(MESSAGE_RESOURCE, "plain");

        String text = message.getEntry("text", LOCALE, UTC);

        assertEquals("Plain localized text", text);
    }

    @Test
    void getEntryLoadsResourceBundleWithConfiguredClassLoader() throws Exception {
        LocalizedMessage message = new LocalizedMessage(
            MESSAGE_RESOURCE, "formatted", new Object[] {"Bouncy Castle"});
        message.setClassLoader(LocalizedMessageTest.class.getClassLoader());
        message.setExtraArgument("!");

        String text = message.getEntry("text", LOCALE, UTC);

        assertEquals("Hello, Bouncy Castle!", text);
    }
}
