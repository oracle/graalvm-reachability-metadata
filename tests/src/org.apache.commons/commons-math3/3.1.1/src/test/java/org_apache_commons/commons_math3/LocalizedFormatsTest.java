/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.junit.jupiter.api.Test;

public class LocalizedFormatsTest {
    @Test
    void returnsTranslatedMessageFromResourceBundleForSupportedLocale() {
        String localized = LocalizedFormats.ARGUMENT_OUTSIDE_DOMAIN.getLocalizedString(Locale.FRANCE);

        assertThat(localized).isEqualTo("argument {0} hors domaine [{1} ; {2}]");
    }

    @Test
    void fallsBackToSourceMessageWhenLocaleHasNoTranslation() {
        LocalizedFormats format = LocalizedFormats.ARGUMENT_OUTSIDE_DOMAIN;

        String localized = format.getLocalizedString(Locale.GERMAN);

        assertThat(localized).isEqualTo(format.getSourceString());
    }
}
