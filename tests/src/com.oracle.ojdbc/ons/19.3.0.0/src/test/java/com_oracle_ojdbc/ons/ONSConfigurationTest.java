/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ons;

import oracle.ons.ONSConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ONSConfigurationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesRemoteConfigurationString() throws Exception {
        ONSConfiguration configuration = ONSConfiguration.getRemoteConfigFromString("""
                nodes=127.0.0.1:6200,127.0.0.1:6201
                remotetimeout=15000
                maxconnections=2
                """);

        assertEquals(15000L, configuration.getSocketTimeout());
        assertEquals(1, configuration.getTopologies().size());
        assertTrue(configuration.toString().contains("127.0.0.1:6200"));
        assertTrue(configuration.toString().contains("127.0.0.1:6201"));
        assertFalse(configuration.hasSecureConnection());
        configuration.setComponent("orders");
        configuration.setInstanceId("orders-primary");
        configuration.setFormFactor("standalone");
        assertEquals("orders", configuration.getComponent());
        assertEquals("orders-primary", configuration.getInstanceId());
        assertEquals("standalone", configuration.getFormFactor());
    }

    @Test
    void loadsRemoteTopologiesFromPropertiesFile() throws Exception {
        Path configFile = temporaryDirectory.resolve("remote.properties");
        Files.writeString(configFile, """
                oracle.ons.nodes.primary=127.0.0.1:6200
                oracle.ons.nodes.backup=127.0.0.1:6201
                oracle.ons.active.primary=true
                oracle.ons.active.backup=false
                oracle.ons.remotetimeout=20000
                """);

        ONSConfiguration configuration = ONSConfiguration.getRemoteConfigFromFile(configFile.toString());

        assertEquals(20000L, configuration.getSocketTimeout());
        assertEquals(2, configuration.getTopologies().size());
        assertTrue(configuration.toString().contains("127.0.0.1:6200"));
        assertTrue(configuration.toString().contains("127.0.0.1:6201"));
        assertFalse(configuration.hasSecureConnection());
    }

    @Test
    void loadsLocalPortsAndOracleHomeFromConfigFile() throws Exception {
        Path configFile = temporaryDirectory.resolve("ons.config");
        Files.writeString(configFile, """
                localport=6100
                remoteport=6200
                remotetimeout=15000
                """);

        ONSConfiguration configuration = ONSConfiguration.getLocalConfigFromFile(
                temporaryDirectory.toString(), configFile.toFile(), "standalone");

        assertEquals(6100, configuration.getLocalPort());
        assertEquals(6200, configuration.getRemotePort());
        assertEquals(15000L, configuration.getSocketTimeout());
        assertEquals(temporaryDirectory.toString(), configuration.getOracleHome());
        assertEquals("standalone", configuration.getFormFactor());
        assertEquals(1, configuration.getTopologies().size());
        assertTrue(configuration.toString().contains("localhost:6100"));
    }

    @Test
    void loadsNamespacedOpmnXmlConfiguration() throws Exception {
        Path configDirectory = Files.createDirectories(temporaryDirectory.resolve("config").resolve("OPMN").resolve("opmn"));
        Files.writeString(configDirectory.resolve(".formfactor"), "standalone\n");
        Files.writeString(configDirectory.resolve("opmn.xml"), """
                <opmn xmlns="urn:example:opmn">
                    <notification-server>
                        <port local="6100" remote="6200"/>
                    </notification-server>
                </opmn>
                """);

        ONSConfiguration configuration = ONSConfiguration.getConfigFromOPMN(temporaryDirectory.toString());

        assertEquals(6100, configuration.getLocalPort());
        assertEquals(temporaryDirectory.toString(), configuration.getOracleHome());
        assertEquals("standalone", configuration.getFormFactor());
        assertEquals(1, configuration.getTopologies().size());
        assertTrue(configuration.toString().contains("localhost:6100"));
        assertFalse(configuration.hasSecureConnection());
    }
}
