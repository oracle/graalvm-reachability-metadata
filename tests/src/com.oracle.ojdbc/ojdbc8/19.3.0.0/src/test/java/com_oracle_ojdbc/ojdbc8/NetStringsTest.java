/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import oracle.net.jdbc.nl.NetStrings;
import org.junit.jupiter.api.Test;

public class NetStringsTest {
    @Test
    void formatsNamingDiagnosticsWithExplicitAndDefaultLocales() {
        NetStrings named = new NetStrings("oracle.net.jdbc.nl.mesg.NLSR", Locale.ENGLISH);
        NetStrings english = new NetStrings(Locale.ENGLISH);
        NetStrings defaults = new NetStrings();

        String key = "NoFile-04600";
        assertThat(named.getString(key, new Object[] {"connections.ora"}))
                .contains("TNS-04600", "connections.ora");
        assertThat(english.getString(key)).isEqualTo(named.getString(key));
        assertThat(defaults.getString(key, new Object[] {"connections.ora"}))
                .contains("TNS-04600", "connections.ora");
    }
}
