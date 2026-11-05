/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_core;

import org.glassfish.grizzly.localization.LocalizableMessageFactory;
import org.glassfish.grizzly.localization.Localizer;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizerTest {
    @Test
    void formatsMessagesFromTheLibraryResourceBundle() {
        Localizer localizer = new Localizer(Locale.ENGLISH);
        LocalizableMessageFactory messages = new LocalizableMessageFactory("org.glassfish.grizzly.localization.log");

        assertThat(localizer.localize(messages.getMessage("warning.grizzly.config.ssl.unknown.cipher.error", "TEST_CIPHER")))
                .isEqualTo("GRIZZLY0010: Unrecognized cipher [TEST_CIPHER].");
        assertThat(localizer.localize(messages.getMessage("warning.grizzly.config.ssl.unknown.cipher.error", "OTHER_CIPHER")))
                .isEqualTo("GRIZZLY0010: Unrecognized cipher [OTHER_CIPHER].");
    }

    @Test
    void resolvesTopLevelBundlesAndLocalizesNestedArguments() {
        Localizer localizer = new Localizer(Locale.ENGLISH);
        LocalizableMessageFactory messages = new LocalizableMessageFactory("org_glassfish_grizzly.grizzly_core.CoreMessages");

        assertThat(localizer.localize(messages.getMessage("greeting", messages.getMessage("recipient"))))
                .isEqualTo("Welcome, traveler!");
        assertThat(localizer.localize(messages.getMessage("unrecognized"))).isEqualTo("Unknown message");
    }
}
