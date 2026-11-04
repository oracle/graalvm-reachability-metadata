/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.builder.fluent.PropertiesBuilderParameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Fluent configuration builders load and persist an order service's properties file with application-specific parameters.
public class PropertiesBuilderConfigurationTest {
    @Test
    @Timeout(60)
    public void loadsConfiguresAndReloadsProperties() throws IOException, ConfigurationException {
        Path propertiesFile = Files.createTempFile("order-service", ".properties");
        try {
            Files.writeString(propertiesFile, """
                    service.name=order-api
                    service.timeout-seconds=30
                    service.endpoints=https://orders.internal/api,https://orders-backup.internal/api
                    """);

            PropertiesBuilderParameters propertiesParameters = new Parameters().properties();
            propertiesParameters
                    .setFile(propertiesFile.toFile())
                    .setListDelimiterHandler(new DefaultListDelimiterHandler(','));
            FileBasedConfigurationBuilder<PropertiesConfiguration> builder =
                    new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class)
                            .configure(propertiesParameters);

            PropertiesConfiguration configuration = builder.getConfiguration();
            assertEquals("order-api", configuration.getString("service.name"));
            assertEquals(30, configuration.getInt("service.timeout-seconds"));
            assertEquals(
                    List.of("https://orders.internal/api", "https://orders-backup.internal/api"),
                    configuration.getList("service.endpoints"));

            configuration.setProperty("service.timeout-seconds", 45);
            configuration.addProperty("service.region", "eu-west");
            builder.save();
            builder.resetResult();

            PropertiesConfiguration reloaded = builder.getConfiguration();
            assertEquals(45, reloaded.getInt("service.timeout-seconds"));
            assertEquals("eu-west", reloaded.getString("service.region"));
            assertEquals(
                    List.of("https://orders.internal/api", "https://orders-backup.internal/api"),
                    reloaded.getList("service.endpoints"));
        } finally {
            Files.deleteIfExists(propertiesFile);
        }
    }
}
