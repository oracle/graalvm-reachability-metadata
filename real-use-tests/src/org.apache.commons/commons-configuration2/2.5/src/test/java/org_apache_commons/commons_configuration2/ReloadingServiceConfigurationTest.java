/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.ReloadingFileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.builder.fluent.PropertiesBuilderParameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.reloading.ReloadingController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Reloading file-based configuration lets a service apply an operator's settings update without restarting.
public class ReloadingServiceConfigurationTest {
    @Test
    @Timeout(60)
    public void reloadsUpdatedServiceSettingsOnDemand() throws IOException, ConfigurationException {
        Path propertiesFile = Files.createTempFile("order-service-reloading", ".properties");
        try {
            Files.writeString(propertiesFile, "service.timeout-seconds=30\nservice.endpoints=orders-a,orders-b\n");

            PropertiesBuilderParameters parameters = new Parameters().properties();
            parameters
                    .setFile(propertiesFile.toFile())
                    .setListDelimiterHandler(new DefaultListDelimiterHandler(','));
            ReloadingFileBasedConfigurationBuilder<PropertiesConfiguration> builder =
                    new ReloadingFileBasedConfigurationBuilder<>(PropertiesConfiguration.class)
                            .configure(parameters);

            PropertiesConfiguration initial = builder.getConfiguration();
            assertEquals(30, initial.getInt("service.timeout-seconds"));
            assertEquals(List.of("orders-a", "orders-b"), initial.getList("service.endpoints"));

            Files.writeString(propertiesFile, "service.timeout-seconds=45\nservice.endpoints=orders-a,orders-b,orders-c\n");
            Files.setLastModifiedTime(
                    propertiesFile,
                    FileTime.from(Instant.parse("2030-01-01T00:00:00Z")));

            ReloadingController controller = builder.getReloadingController();
            assertTrue(controller.checkForReloading(null));

            PropertiesConfiguration reloaded = builder.getConfiguration();
            assertEquals(45, reloaded.getInt("service.timeout-seconds"));
            assertEquals(
                    List.of("orders-a", "orders-b", "orders-c"),
                    reloaded.getList("service.endpoints"));
        } finally {
            Files.deleteIfExists(propertiesFile);
        }
    }
}
