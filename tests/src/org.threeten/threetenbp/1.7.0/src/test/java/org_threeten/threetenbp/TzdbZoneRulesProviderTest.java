/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten.threetenbp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NavigableMap;

import org.junit.jupiter.api.Test;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.zone.TzdbZoneRulesProvider;
import org.threeten.bp.zone.ZoneRules;
import org.threeten.bp.zone.ZoneRulesProvider;

public class TzdbZoneRulesProviderTest {
    private static final String SAMPLE_ZONE_ID = "Europe/London";

    @Test
    void loadsBundledTzdbDataThroughDefaultClassLoader() {
        TzdbZoneRulesProvider provider = new TzdbZoneRulesProvider();
        assertEquals("TZDB", provider.toString());

        registerProviderIfServiceLoadingDidNotRegisterIt(provider);

        assertTrue(ZoneRulesProvider.getAvailableZoneIds().contains(SAMPLE_ZONE_ID));
        NavigableMap<String, ZoneRules> versions = ZoneRulesProvider.getVersions(SAMPLE_ZONE_ID);
        assertFalse(versions.isEmpty());

        ZoneRules rules = ZoneRulesProvider.getRules(SAMPLE_ZONE_ID, false);
        assertEquals(ZoneOffset.UTC, rules.getOffset(Instant.parse("2024-01-01T00:00:00Z")));
    }

    private static void registerProviderIfServiceLoadingDidNotRegisterIt(ZoneRulesProvider provider) {
        if (!ZoneRulesProvider.getAvailableZoneIds().contains(SAMPLE_ZONE_ID)) {
            ZoneRulesProvider.registerProvider(provider);
        }
    }
}
