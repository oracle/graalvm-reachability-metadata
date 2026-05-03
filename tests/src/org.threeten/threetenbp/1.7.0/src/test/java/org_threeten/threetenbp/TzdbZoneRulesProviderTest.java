/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten.threetenbp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.NavigableMap;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.threeten.bp.zone.TzdbZoneRulesProvider;
import org.threeten.bp.zone.ZoneRules;
import org.threeten.bp.zone.ZoneRulesProvider;

public class TzdbZoneRulesProviderTest {
    @Test
    void defaultProviderLoadsTzdbResourceFromClasspath() {
        TzdbZoneRulesProvider provider = new TzdbZoneRulesProvider();
        if (ZoneRulesProvider.getAvailableZoneIds().isEmpty()) {
            ZoneRulesProvider.registerProvider(provider);
        }

        Set<String> zoneIds = ZoneRulesProvider.getAvailableZoneIds();
        ZoneRules londonRules = ZoneRulesProvider.getRules("Europe/London", false);
        NavigableMap<String, ZoneRules> londonVersions = ZoneRulesProvider.getVersions("Europe/London");

        assertThat(provider).isInstanceOf(ZoneRulesProvider.class);
        assertThat(zoneIds).contains("Europe/London", "America/New_York", "Asia/Tokyo");
        assertThat(londonRules.isFixedOffset()).isFalse();
        assertThat(londonVersions).isNotEmpty();
    }
}
