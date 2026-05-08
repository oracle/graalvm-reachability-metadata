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
    void localizableMessageSuppliesServletResourceBundle() {
        Localizable localizable = LocalizationMessages.localizableHEADER_VALUE_READ_FAILED();

        ResourceBundle resourceBundle = localizable.getResourceBundle(Locale.ROOT);

        assertThat(resourceBundle.getString("header.value.read.failed"))
                .isEqualTo("Attempt to read the header value failed.");
    }
}
