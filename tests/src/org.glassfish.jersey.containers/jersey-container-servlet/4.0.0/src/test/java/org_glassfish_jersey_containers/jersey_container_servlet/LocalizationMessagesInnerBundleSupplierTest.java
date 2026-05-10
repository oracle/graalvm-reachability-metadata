/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_containers.jersey_container_servlet;

import java.util.Locale;
import java.util.ResourceBundle;

import org.glassfish.jersey.internal.l10n.Localizable;
import org.glassfish.jersey.servlet.internal.l10n.LocalizationMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizationMessagesInnerBundleSupplierTest {
    @Test
    void localizableMessageExposesServletInitializationResourceBundle() {
        Localizable message = LocalizationMessages.localizableJERSEY_APP_NO_MAPPING("SampleApplication");

        ResourceBundle bundle = message.getResourceBundle(Locale.ROOT);

        assertThat(bundle.getString("jersey.app.no.mapping"))
                .isEqualTo("The Jersey servlet application, named {0}, has no servlet mapping.");
    }
}
