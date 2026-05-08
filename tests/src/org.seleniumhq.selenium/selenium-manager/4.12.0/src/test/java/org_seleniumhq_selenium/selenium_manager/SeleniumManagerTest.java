/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_manager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.manager.SeleniumManager;

public class SeleniumManagerTest {
    @Test
    @Timeout(30)
    void extractsBundledManagerBinaryBeforeRunningBrowserResolution() {
        ImmutableCapabilities capabilities = new ImmutableCapabilities("browserName", "unknown-browser-for-test");

        assertThatThrownBy(() -> SeleniumManager.getInstance().getDriverPath(capabilities, true))
                .isInstanceOf(WebDriverException.class)
                .hasMessageContaining("Invalid browser name: unknown-browser-for-test");
    }
}
