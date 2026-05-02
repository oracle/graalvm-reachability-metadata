/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.apache.commons.configuration2.XMLPropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;

public class XMLPropertiesConfigurationAnonymous1Test {
    @Test
    void resolvesBundledPropertiesDtdWhenReadingXmlProperties() throws ConfigurationException {
        final XMLPropertiesConfiguration configuration = new XMLPropertiesConfiguration();
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
                <properties>
                  <comment>Integration settings</comment>
                  <entry key="endpoint">https://example.invalid/service</entry>
                  <entry key="retries">3</entry>
                </properties>
                """.stripLeading();

        configuration.read(new StringReader(xml));

        assertThat(configuration.getHeader()).isEqualTo("Integration settings");
        assertThat(configuration.getString("endpoint")).isEqualTo("https://example.invalid/service");
        assertThat(configuration.getInt("retries")).isEqualTo(3);
    }
}
