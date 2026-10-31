/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.joda.time.DateTimeZone;
import io.trino.jdbc.$internal.joda.time.tz.DefaultNameProvider;
import io.trino.jdbc.$internal.joda.time.tz.NameProvider;
import io.trino.jdbc.$internal.joda.time.tz.Provider;
import io.trino.jdbc.$internal.joda.time.tz.UTCProvider;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateTimeZoneTest {
    @Test
    @ResourceLock("joda-zone-providers")
    void discoversConfiguredZoneAndNameProviders() {
        String zoneKey = "io.trino.jdbc.$internal.joda.time.DateTimeZone.Provider";
        String nameKey = "io.trino.jdbc.$internal.joda.time.DateTimeZone.NameProvider";
        String oldZone = System.getProperty(zoneKey);
        String oldName = System.getProperty(nameKey);
        Provider previousProvider = DateTimeZone.getProvider();
        NameProvider previousNames = DateTimeZone.getNameProvider();
        try {
            System.setProperty(zoneKey, UTCProvider.class.getName());
            System.setProperty(nameKey, DefaultNameProvider.class.getName());
            DateTimeZone.setProvider(null);
            DateTimeZone.setNameProvider(null);
            assertEquals(DateTimeZone.UTC, DateTimeZone.forID("UTC"));
            assertEquals("Coordinated Universal Time", DateTimeZone.UTC.getName(0, Locale.ENGLISH));
        } finally {
            restoreProperty(zoneKey, oldZone);
            restoreProperty(nameKey, oldName);
            DateTimeZone.setProvider(previousProvider);
            DateTimeZone.setNameProvider(previousNames);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
