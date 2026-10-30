/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oceanbase.oceanbase_client;

import com.oceanbase.jdbc.UrlParser;
import com.oceanbase.jdbc.util.Options;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultOptionsTest {
    @Test
    void parsesTypedPropertiesAndPreservesExplicitPropertyPrecedence() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", "application");
        properties.setProperty("connectTimeout", "20000");
        UrlParser parser = UrlParser.parse("jdbc:oceanbase://localhost/inventory?user=url-user"
                + "&connectTimeout=30000&socketTimeout=40000&allowMultiQueries=true&tcpNoDelay=false"
                + "&cachePrepStmts=1&useCompression=0&slowQueryThresholdNanos=5000000000", properties);
        Options options = parser.getOptions();

        assertThat(parser.getUsername()).isEqualTo("application");
        assertThat(options.connectTimeout).isEqualTo(20000);
        assertThat(options.socketTimeout).isEqualTo(40000);
        assertThat(options.allowMultiQueries).isTrue();
        assertThat(options.tcpNoDelay).isFalse();
        assertThat(options.cachePrepStmts).isTrue();
        assertThat(options.useCompression).isFalse();
        assertThat(options.slowQueryThresholdNanos).isEqualTo(5000000000L);
    }

    @Test
    void rendersConfiguredOptionsIntoAReusableConnectionUrl() throws Exception {
        UrlParser original = UrlParser.parse("jdbc:oceanbase://localhost/inventory?user=reporting"
                + "&connectTimeout=20000&socketTimeout=30000&allowMultiQueries=true&tcpNoDelay=false"
                + "&slowQueryThresholdNanos=6000000000");
        UrlParser configured = new UrlParser(original.getDatabase(), original.getHostAddresses(),
                original.getOptions(), original.getHaMode());
        String url = configured.toString();
        assertThat(url).contains("user=reporting", "connectTimeout=20000", "tcpNoDelay=false",
                "allowMultiQueries=true", "slowQueryThresholdNanos=6000000000");
        Options restored = UrlParser.parse(url).getOptions();
        assertThat(restored.user).isEqualTo("reporting");
        assertThat(restored.connectTimeout).isEqualTo(20000);
        assertThat(restored.socketTimeout).isEqualTo(30000);
        assertThat(restored.allowMultiQueries).isTrue();
        assertThat(restored.tcpNoDelay).isFalse();
        assertThat(restored.slowQueryThresholdNanos).isEqualTo(6000000000L);
    }
}
