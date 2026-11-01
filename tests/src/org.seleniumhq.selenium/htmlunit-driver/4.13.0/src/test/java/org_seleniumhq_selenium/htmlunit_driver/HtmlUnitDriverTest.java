/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.htmlunit_driver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HtmlUnitDriverTest {
    @Test
    @Timeout(120)
    void returnsJavaScriptDateAsUtcTimestamp() {
        HtmlUnitDriver driver = new HtmlUnitDriver(true);
        try {
            Object result = driver.executeScript("""
                    return new Date(Date.UTC(2024, 1, 29, 12, 34, 56, 789));
                    """);

            assertEquals("2024-02-29T12:34:56.789Z", result);
        } finally {
            driver.quit();
        }
    }
}
