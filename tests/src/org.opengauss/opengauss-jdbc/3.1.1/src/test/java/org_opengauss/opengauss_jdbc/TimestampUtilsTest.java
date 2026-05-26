/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.core.Provider;
import org.postgresql.jdbc.TimestampUtils;

import java.sql.Date;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

public class TimestampUtilsTest {
    @Test
    void decodesBinaryDateUsingDefaultTimeZoneWhenNoTimeZoneIsProvided() throws Exception {
        Provider<TimeZone> timeZoneProvider = TimeZone::getDefault;
        TimestampUtils timestampUtils = new TimestampUtils(false, timeZoneProvider);

        timestampUtils.hasFastDefaultTimeZone();
        Date date = timestampUtils.toDateBin(null, new byte[] {0, 0, 0, 0});

        assertThat(date).isNotNull();
    }
}
