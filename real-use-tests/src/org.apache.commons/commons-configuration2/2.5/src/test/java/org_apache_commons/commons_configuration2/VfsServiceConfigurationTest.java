/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.commons.configuration2.io.VFSFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// VFS-backed file handling lets an order service load and persist settings through a virtual file system.
public class VfsServiceConfigurationTest {
    @Test
    @Timeout(60)
    public void loadsAndPersistsSettingsThroughVfs() throws IOException, ConfigurationException {
        Path propertiesFile = Files.createTempFile("order-service-vfs", ".properties");
        URL propertiesUrl = propertiesFile.toUri().toURL();
        try {
            Files.writeString(propertiesFile, "service.name=order-api\nservice.timeout-seconds=30\n");

            PropertiesConfiguration configuration = new PropertiesConfiguration();
            FileHandler fileHandler = new FileHandler(configuration);
            fileHandler.setFileSystem(new VFSFileSystem());
            fileHandler.setURL(propertiesUrl);
            fileHandler.load();

            assertEquals("order-api", configuration.getString("service.name"));
            assertEquals(30, configuration.getInt("service.timeout-seconds"));

            configuration.setProperty("service.timeout-seconds", 45);
            fileHandler.save();

            PropertiesConfiguration reloaded = new PropertiesConfiguration();
            FileHandler reloadedFileHandler = new FileHandler(reloaded);
            reloadedFileHandler.setFileSystem(new VFSFileSystem());
            reloadedFileHandler.setURL(propertiesUrl);
            reloadedFileHandler.load();

            assertEquals("order-api", reloaded.getString("service.name"));
            assertEquals(45, reloaded.getInt("service.timeout-seconds"));
            assertTrue(Files.readString(propertiesFile).contains("service.timeout-seconds=45"));
        } finally {
            Files.deleteIfExists(propertiesFile);
        }
    }
}
