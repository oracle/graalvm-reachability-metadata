/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2.plist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.plist.XMLPropertyListConfiguration;
import org.junit.jupiter.api.Test;

public class XMLPropertyListConfigurationAnonymous1Test {
    @Test
    void resolvesBundledPropertyListDtdWhenReadingXmlPropertyList() throws ConfigurationException {
        final XMLPropertyListConfiguration configuration = new XMLPropertyListConfiguration();
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                  <dict>
                    <key>serviceName</key>
                    <string>orders</string>
                    <key>enabled</key>
                    <true/>
                    <key>ports</key>
                    <array>
                      <integer>8080</integer>
                      <integer>8443</integer>
                    </array>
                  </dict>
                </plist>
                """.stripLeading();

        configuration.read(new StringReader(xml));

        assertThat(configuration.getString("serviceName")).isEqualTo("orders");
        assertThat(configuration.getBoolean("enabled")).isTrue();
        assertThat(configuration.getList(Integer.class, "ports")).containsExactly(8080, 8443);
    }
}
