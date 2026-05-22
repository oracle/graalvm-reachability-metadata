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
    public void localizesMessageFromJerseyResourceBundle() {
        final Localizer localizer = new Localizer(Locale.ENGLISH);
        final LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.jersey.impl.impl");
        final Localizable message = messageFactory.getMessage("illegal.initial.capacity", 7);

        final String localized = localizer.localize(message);

        assertThat(localized).isEqualTo("Illegal initial capacity: 7.");
    }

    @Test
    public void fallsBackToTopLevelBundleName() {
        final Localizer localizer = new Localizer(Locale.ENGLISH);
        final LocalizableMessageFactory messageFactory = new LocalizableMessageFactory(
                "com.sun.jersey.test.LocalizerFallbackBundle");
        final Localizable message = messageFactory.getMessage("fallback.message", "argument");

        final String localized = localizer.localize(message);

        assertThat(localized).isEqualTo("Top-level fallback bundle resolved argument.");
    }
}
