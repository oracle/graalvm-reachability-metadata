/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.DataConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Typed configuration conversion supplies an order service with application-ready values from generic settings.
public class TypedServiceConfigurationTest {
    @Test
    @Timeout(60)
    public void convertsStoredSettingsToApplicationTypes() {
        BaseConfiguration rawConfiguration = new BaseConfiguration();
        rawConfiguration.addProperty("service.ports", 8080);
        rawConfiguration.addProperty("service.ports", 8443);
        rawConfiguration.addProperty("service.endpoint", "https://orders.example/api");
        rawConfiguration.addProperty("service.locale", "en_US");

        DataConfiguration configuration = new DataConfiguration(rawConfiguration);

        assertArrayEquals(new int[] {8080, 8443}, configuration.getIntArray("service.ports"));
        assertEquals(URI.create("https://orders.example/api"), configuration.getURI("service.endpoint"));
        assertEquals(Locale.US, configuration.getLocale("service.locale"));
    }
}
