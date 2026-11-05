/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.ListFormatter;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's list-formatting subsystem to render delivery recipients for localized notifications.
public class DeliveryRecipientListFormattingTest {
    @Test
    public void formatsRecipientListsForDifferentLocales() {
        DeliveryNotification notification = new DeliveryNotification();

        assertEquals("Maya, Noah, and Lina", notification.recipients(ULocale.ENGLISH));
        assertEquals("Maya, Noah et Lina", notification.recipients(ULocale.FRENCH));
        assertEquals("Maya and Noah", notification.twoRecipients(ULocale.ENGLISH));
    }

    private static final class DeliveryNotification {
        private String recipients(ULocale locale) {
            ListFormatter formatter = ListFormatter.getInstance(locale);
            return formatter.format("Maya", "Noah", "Lina");
        }

        private String twoRecipients(ULocale locale) {
            ListFormatter formatter = ListFormatter.getInstance(locale);
            return formatter.format("Maya", "Noah");
        }
    }
}
