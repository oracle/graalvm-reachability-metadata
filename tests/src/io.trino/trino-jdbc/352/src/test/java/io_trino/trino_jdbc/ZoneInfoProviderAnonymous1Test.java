/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.joda.time.DateTimeZone;
import io.trino.jdbc.$internal.joda.time.tz.ZoneInfoProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZoneInfoProviderAnonymous1Test {
    @Test
    void loadsZoneRulesThroughExplicitAndSystemClassLoaders() throws Exception {
        ZoneInfoProvider explicit = new ZoneInfoProvider(DateTimeZone.DEFAULT_TZ_DATA_PATH);
        ZoneInfoProvider system = new ZoneInfoProvider(DateTimeZone.DEFAULT_TZ_DATA_PATH, null);
        long winter = Instant.parse("2020-01-15T12:00:00Z").toEpochMilli();
        long summer = Instant.parse("2020-07-15T12:00:00Z").toEpochMilli();
        for (ZoneInfoProvider provider : new ZoneInfoProvider[] {explicit, system}) {
            assertTrue(provider.getAvailableIDs().contains("Europe/Paris"));
            assertEquals(3_600_000, provider.getZone("Europe/Paris").getOffset(winter));
            assertEquals(7_200_000, provider.getZone("Europe/Paris").getOffset(summer));
        }
    }
}
