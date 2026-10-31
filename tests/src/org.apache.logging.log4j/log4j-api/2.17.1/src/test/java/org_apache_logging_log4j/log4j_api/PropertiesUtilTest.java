/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.apache.logging.log4j.util.PropertiesUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesUtilTest {

    @Test
    void resolvesCharsetAliasFromLibraryResourceBundle() {
        Properties properties = new Properties();
        properties.setProperty("cp65001", "unavailable-charset");
        PropertiesUtil util = new PropertiesUtil(properties);

        assertThat(util.getCharsetProperty("cp65001", StandardCharsets.UTF_16)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void usesDefaultWhenCharsetAndAliasAreUnknown() {
        Properties properties = new Properties();
        properties.setProperty("orders.charset", "unavailable-charset");
        PropertiesUtil util = new PropertiesUtil(properties);

        assertThat(util.getCharsetProperty("orders.charset", StandardCharsets.US_ASCII))
                .isEqualTo(StandardCharsets.US_ASCII);
    }
}
