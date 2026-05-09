/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_inject.jersey_hk2;

import java.util.Locale;
import java.util.ResourceBundle;

import org.glassfish.jersey.inject.hk2.LocalizationMessages;
import org.glassfish.jersey.internal.l10n.Localizable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizationMessagesInnerBundleSupplierTest {
    @Test
    void localizableMessageProvidesResourceBundleThroughBundleSupplier() {
        String detail = "service locator failed";

        Localizable localizable = LocalizationMessages.localizableHK_2_UNKNOWN_ERROR(detail);
        ResourceBundle resourceBundle = localizable.getResourceBundle(Locale.ENGLISH);

        assertThat(resourceBundle.getString(localizable.getKey()))
                .isEqualTo("Unknown HK2 failure detected:\n{0}");
        assertThat(localizable.getArguments()).containsExactly(detail);
    }
}
