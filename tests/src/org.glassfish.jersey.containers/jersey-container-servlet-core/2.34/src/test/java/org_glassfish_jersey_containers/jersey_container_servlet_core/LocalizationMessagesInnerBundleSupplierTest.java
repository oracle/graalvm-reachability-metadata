/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_containers.jersey_container_servlet_core;

import java.util.Locale;
import java.util.ResourceBundle;

import org.glassfish.jersey.internal.l10n.Localizable;
import org.glassfish.jersey.servlet.internal.LocalizationMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizationMessagesInnerBundleSupplierTest {
    @Test
    void localizableMessageProvidesServletResourceBundleThroughBundleSupplier() {
        String regex = "[";
        String parameterName = "jersey.config.servlet.filter.staticContentRegex";

        Localizable localizable = LocalizationMessages.localizableINIT_PARAM_REGEX_SYNTAX_INVALID(regex, parameterName);
        ResourceBundle resourceBundle = localizable.getResourceBundle(Locale.ENGLISH);

        assertThat(resourceBundle.getString(localizable.getKey()))
                .isEqualTo("The syntax is invalid for the regular expression \"{0}\" associated with "
                        + "the initialization parameter \"{1}\".");
        assertThat(localizable.getArguments()).containsExactly(regex, parameterName);
    }
}
