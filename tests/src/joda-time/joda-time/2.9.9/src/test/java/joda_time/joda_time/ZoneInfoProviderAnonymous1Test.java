/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time.joda_time;

import static org.assertj.core.api.Assertions.assertThat;

import org.joda.time.tz.ZoneInfoProvider;
import org.junit.jupiter.api.Test;

public class ZoneInfoProviderAnonymous1Test {

    @Test
    void loadsZoneInfoMapThroughExplicitClassLoader() throws Exception {
        ZoneInfoProvider provider = new ZoneInfoProvider(
                "org/joda/time/tz/data",
                ZoneInfoProviderAnonymous1Test.class.getClassLoader()
        );

        assertThat(provider.getAvailableIDs()).contains("UTC", "Europe/Paris");
    }

    @Test
    void loadsZoneInfoMapThroughSystemClassLoader() throws Exception {
        ZoneInfoProvider provider = new ZoneInfoProvider("org/joda/time/tz/data", null);

        assertThat(provider.getAvailableIDs()).contains("UTC", "Europe/Paris");
    }
}
