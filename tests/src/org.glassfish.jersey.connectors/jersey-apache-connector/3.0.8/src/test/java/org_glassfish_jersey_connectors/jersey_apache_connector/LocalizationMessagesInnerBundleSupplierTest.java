/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_connectors.jersey_apache_connector;

import java.util.Locale;
import java.util.ResourceBundle;

import org.glassfish.jersey.apache.connector.LocalizationMessages;
import org.glassfish.jersey.internal.l10n.Localizable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizationMessagesInnerBundleSupplierTest {
    @Test
    void localizableMessageSuppliesApacheConnectorResourceBundle() {
        Localizable message = LocalizationMessages.localizableERROR_BUFFERING_ENTITY();

        ResourceBundle bundle = message.getResourceBundle(Locale.ROOT);

        assertThat(bundle.getString(message.getKey())).isEqualTo("Error buffering the entity.");
    }
}
