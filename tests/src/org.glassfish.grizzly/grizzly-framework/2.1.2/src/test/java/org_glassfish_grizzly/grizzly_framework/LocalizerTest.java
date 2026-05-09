/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.localization.Localizable;
import org.glassfish.grizzly.localization.Localizer;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizerTest {
    private static final String PACKAGE_BUNDLE =
            "org_glassfish_grizzly.grizzly_framework.LocalizerMessages";
    private static final String FALLBACK_BUNDLE =
            "org_glassfish_grizzly.grizzly_framework.LocalizerFallbackMessages";

    @Test
    void localizesMessageFromPackageResourceBundle() {
        Localizer localizer = new Localizer(Locale.US);

        String message = localizer.localize(localizable(PACKAGE_BUNDLE, "greeting", "Grizzly"));

        assertThat(message).isEqualTo("Hello Grizzly");
    }

    @Test
    void localizesMessageFromTopLevelFallbackBundle() {
        Localizer localizer = new Localizer(Locale.US);

        String message = localizer.localize(localizable(FALLBACK_BUNDLE, "fallback", "resource"));

        assertThat(message).isEqualTo("Fallback resource");
    }

    private static Localizable localizable(String bundleName, String key, Object... arguments) {
        return new Localizable() {
            @Override
            public String getKey() {
                return key;
            }

            @Override
            public Object[] getArguments() {
                return arguments;
            }

            @Override
            public String getResourceBundleName() {
                return bundleName;
            }
        };
    }
}
