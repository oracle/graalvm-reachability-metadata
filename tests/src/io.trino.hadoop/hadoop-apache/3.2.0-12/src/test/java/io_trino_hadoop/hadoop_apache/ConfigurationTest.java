/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigurationTest {
    @Test
    void loadsBundledDefaults() {
        Configuration configuration = new Configuration();

        assertEquals("file:///", configuration.get("fs.defaultFS"));
        assertEquals(4096, configuration.getInt("io.file.buffer.size", -1));
        assertEquals("simple", configuration.get("hadoop.security.authentication"));
    }

    @Test
    void mergesXmlResourcesAndResolvesSubstitutions() {
        Configuration configuration = new Configuration(false);
        String base = """
                <configuration>
                  <property><name>dataset.root</name><value>warehouse</value><final>true</final></property>
                  <property><name>dataset.path</name><value>${dataset.root}/events</value></property>
                  <property><name>batch.size</name><value>23</value></property>
                </configuration>
                """;
        String overlay = """
                <configuration>
                  <property><name>dataset.root</name><value>ignored</value></property>
                  <property><name>batch.size</name><value>47</value></property>
                  <property><name>checks.enabled</name><value>true</value></property>
                </configuration>
                """;
        configuration.addResource(new ByteArrayInputStream(base.getBytes(StandardCharsets.UTF_8)));
        configuration.addResource(new ByteArrayInputStream(overlay.getBytes(StandardCharsets.UTF_8)));

        assertEquals("warehouse/events", configuration.get("dataset.path"));
        assertEquals(47, configuration.getInt("batch.size", -1));
        assertTrue(configuration.getBoolean("checks.enabled", false));
        assertTrue(configuration.getFinalParameters().contains("dataset.root"));
        Configuration copy = new Configuration(configuration);
        copy.setInt("batch.size", 64);
        assertEquals(47, configuration.getInt("batch.size", -1));
        assertEquals(64, copy.getInt("batch.size", -1));
    }
}
