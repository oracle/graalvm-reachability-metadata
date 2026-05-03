/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.graalvm.internal.tck.NativeImageSupport;
import org.h2.tools.Upgrade;
import org.junit.jupiter.api.Test;

import java.sql.Driver;

import static org.assertj.core.api.Assertions.assertThat;

public class UpgradeAnonymous1Test {
    @Test
    void oldH2DriverIsLoadedThroughUpgradeClassLoader() throws Exception {
        Driver driver = null;
        try {
            driver = Upgrade.loadH2(200);

            assertThat(driver.acceptsURL("jdbc:h2:mem:upgradeLoadedDriver")).isTrue();
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        } finally {
            if (driver != null) {
                Upgrade.unloadH2(driver);
            }
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
