/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.localization.Localizable;
import com.sun.jersey.localization.LocalizableMessageFactory;
import com.sun.jersey.localization.Localizer;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizerTest {
    @Test
    void localizesMessageFromConfiguredResourceBundle() {
        LocalizableMessageFactory messageFactory = new LocalizableMessageFactory(
                "com_sun_jersey.jersey_core.LocalizerMessages");
        Localizable message = messageFactory.getMessage("greeting", "Jersey");

        String localizedMessage = new Localizer(Locale.ROOT).localize(message);

        assertThat(localizedMessage).isEqualTo("Hello Jersey");
    }

    @Test
    void localizesMessageFromAlternateTopLevelResourceBundle() {
        LocalizableMessageFactory messageFactory = new LocalizableMessageFactory(
                "com_sun_jersey.jersey_core.FallbackBundle");
        Localizable message = messageFactory.getMessage("fallback", "Jersey");

        String localizedMessage = new Localizer(Locale.ROOT).localize(message);

        assertThat(localizedMessage).isEqualTo("Fallback Jersey");
    }
}
