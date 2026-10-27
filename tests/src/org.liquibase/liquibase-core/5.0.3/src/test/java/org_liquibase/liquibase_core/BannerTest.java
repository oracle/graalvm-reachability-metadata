/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.GlobalConfiguration;
import liquibase.integration.commandline.Banner;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class BannerTest {

    @Test
    void toStringLoadsLocalizedCoreMessages() {
        Locale previousDefault = Locale.getDefault();
        String showBannerKey = GlobalConfiguration.SHOW_BANNER.getKey();
        String previousShowBanner = System.getProperty(showBannerKey);
        Locale.setDefault(Locale.US);
        System.setProperty(showBannerKey, Boolean.FALSE.toString());
        try {
            Banner banner = new Banner();

            String output = banner.toString();

            assertThat(output)
                    .contains("Starting Liquibase at")
                    .contains("using Java");
        } finally {
            Locale.setDefault(previousDefault);
            restoreProperty(showBannerKey, previousShowBanner);
        }
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
