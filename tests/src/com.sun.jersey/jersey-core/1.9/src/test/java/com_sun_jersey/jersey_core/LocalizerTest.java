/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.localization.Localizable;
import com.sun.jersey.localization.LocalizableMessage;
import com.sun.jersey.localization.LocalizableMessageFactory;
import com.sun.jersey.localization.Localizer;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class LocalizerTest {
    private static final String PACKAGE_BUNDLE_NAME = "com_sun_jersey.jersey_core.LocalizerMessages";
    private static final String FALLBACK_BUNDLE_NAME = "com_sun_jersey.jersey_core.FlatLocalizerMessages";

    @Test
    void localizesMessageFromNamedResourceBundle() {
        final Localizer localizer = new Localizer(Locale.ROOT);
        final LocalizableMessageFactory messages = new LocalizableMessageFactory(PACKAGE_BUNDLE_NAME);

        final String localized = localizer.localize(messages.getMessage("greeting", "Jersey"));

        assertThat(localized).isEqualTo("Hello Jersey from package bundle");
    }

    @Test
    void localizesMessageFromTopLevelFallbackResourceBundle() {
        final Localizer localizer = new Localizer(Locale.ROOT);
        final Localizable nested = new LocalizableMessage("FlatLocalizerMessages", "inner", "fallback");
        final Localizable outer = new LocalizableMessage(FALLBACK_BUNDLE_NAME, "outer", nested);

        final String localized = localizer.localize(outer);

        assertThat(localized).isEqualTo("Outer value contains inner fallback value");
    }
}
