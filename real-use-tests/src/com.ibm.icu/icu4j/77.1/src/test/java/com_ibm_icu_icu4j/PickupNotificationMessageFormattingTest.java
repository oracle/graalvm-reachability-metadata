/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's message-formatting subsystem to render localized pickup notifications.
public class PickupNotificationMessageFormattingTest {
    @Test
    public void rendersNotificationsWithLocaleSpecificPluralMessages() {
        PickupNotification notification = new PickupNotification();

        assertEquals("Maya has no items ready for pickup.", notification.render(ULocale.ENGLISH, "Maya", 0));
        assertEquals("Maya has 1 item ready for pickup.", notification.render(ULocale.ENGLISH, "Maya", 1));
        assertEquals("Maya a 2 articles pr\u00EAts.", notification.render(ULocale.FRENCH, "Maya", 2));
    }

    private static final class PickupNotification {
        private static final String ENGLISH_PATTERN =
                "{name} has {count, plural, =0 {no items} one {# item} other {# items}} ready for pickup.";
        private static final String FRENCH_PATTERN =
                "{name} a {count, plural, =0 {aucun article} one {# article} other {# articles}} pr\u00EAts.";

        private String render(ULocale locale, String customerName, int itemCount) {
            String pattern = ULocale.FRENCH.equals(locale) ? FRENCH_PATTERN : ENGLISH_PATTERN;
            MessageFormat formatter = new MessageFormat(pattern, locale);
            return formatter.format(Map.of("name", customerName, "count", itemCount));
        }
    }
}
