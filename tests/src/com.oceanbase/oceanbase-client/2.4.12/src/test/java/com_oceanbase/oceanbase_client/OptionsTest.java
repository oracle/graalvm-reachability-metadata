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

import static org.assertj.core.api.Assertions.assertThat;

public class OptionsTest {
    @Test
    void describesParsedConnectionSettingsForDiagnostics() throws Exception {
        Options options = UrlParser.parse("jdbc:oceanbase://localhost/catalog?user=diagnostic-user"
                + "&socketTimeout=20000&autocommit=false&slowQueryThresholdNanos=7000000000").getOptions();

        assertThat(options.toString()).contains("Options {", "user: diagnostic-user",
                "socketTimeout: 20000", "autocommit: false", "slowQueryThresholdNanos: 7000000000",
                "tcpKeepAlive: true").endsWith("}");
    }
}
