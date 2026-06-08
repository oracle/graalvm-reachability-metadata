/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration.plist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.math.BigInteger;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.plist.XMLPropertyListConfiguration;
import org.junit.jupiter.api.Test;

public class XMLPropertyListConfigurationAnonymous1Test {
    @Test
    public void loadResolvesBundledPropertyListDtdFromClasspath() throws ConfigurationException {
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

        configuration.load(new StringReader(xml));

        assertThat(configuration.getString("serviceName")).isEqualTo("orders");
        assertThat(configuration.getBoolean("enabled")).isTrue();
        assertThat(configuration.getList("ports"))
                .containsExactly(new BigInteger("8080"), new BigInteger("8443"));
    }
}
