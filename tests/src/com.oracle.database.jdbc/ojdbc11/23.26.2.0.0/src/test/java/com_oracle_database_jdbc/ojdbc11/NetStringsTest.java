/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import oracle.net.jdbc.nl.NetStrings;
import org.junit.jupiter.api.Test;

public class NetStringsTest {
    private static final String BUNDLE_NAME = "oracle.net.jdbc.nl.mesg.NLSR";
    private static final String MESSAGE_KEY = "NoFile-04600";

    @Test
    void loadsMessagesThroughEachSupportedConstructor() {
        NetStrings explicitBundle = new NetStrings(BUNDLE_NAME, Locale.ENGLISH);
        NetStrings explicitLocale = new NetStrings(Locale.ENGLISH);
        NetStrings defaultLocale = new NetStrings();

        assertThat(explicitBundle.getString(MESSAGE_KEY, new Object[] {"tnsnames.ora"}))
                .contains("TNS-04600", "tnsnames.ora");
        assertThat(explicitLocale.getString(MESSAGE_KEY)).contains("TNS-04600");
        assertThat(defaultLocale.getString(MESSAGE_KEY)).contains("TNS-04600");
    }
}
