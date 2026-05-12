/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_support;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.locators.RelativeLocator;

import static org.assertj.core.api.Assertions.assertThat;

public class RelativeLocatorScriptTest {
    @Test
    void findElementsLoadsBundledRelativeLocatorScript() {
        CapturingWebDriver driver = new CapturingWebDriver();

        List<WebElement> elements = RelativeLocator.with(By.tagName("button"))
                .above(By.id("header"))
                .findElements(driver);

        assertThat(elements).isEmpty();
        assertThat(driver.script).startsWith("/* findElements */return (");
        assertThat(driver.arguments).hasSize(1);
        assertThat(driver.arguments[0]).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> locator = (Map<String, Object>) driver.arguments[0];
        assertThat(locator).containsOnlyKeys("relative");

        @SuppressWarnings("unchecked")
        Map<String, Object> relativeLocator = (Map<String, Object>) locator.get("relative");
        assertThat(relativeLocator).containsKeys("root", "filters");
    }

    private static final class CapturingWebDriver implements WebDriver, JavascriptExecutor {
        private String script;
        private Object[] arguments;

        @Override
        public void get(String url) {
            throw new UnsupportedOperationException("Navigation is not needed for this test");
        }

        @Override
        public String getCurrentUrl() {
            throw new UnsupportedOperationException("Current URL is not needed for this test");
        }

        @Override
        public String getTitle() {
            throw new UnsupportedOperationException("Title is not needed for this test");
        }

        @Override
        public List<WebElement> findElements(By by) {
            return List.of();
        }

        @Override
        public WebElement findElement(By by) {
            throw new UnsupportedOperationException("Element lookup is not needed for this test");
        }

        @Override
        public String getPageSource() {
            throw new UnsupportedOperationException("Page source is not needed for this test");
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException("Closing is not needed for this test");
        }

        @Override
        public void quit() {
            throw new UnsupportedOperationException("Quitting is not needed for this test");
        }

        @Override
        public Set<String> getWindowHandles() {
            throw new UnsupportedOperationException("Window handles are not needed for this test");
        }

        @Override
        public String getWindowHandle() {
            throw new UnsupportedOperationException("Window handle is not needed for this test");
        }

        @Override
        public TargetLocator switchTo() {
            throw new UnsupportedOperationException("Target locator is not needed for this test");
        }

        @Override
        public Navigation navigate() {
            throw new UnsupportedOperationException("Navigation is not needed for this test");
        }

        @Override
        public Options manage() {
            throw new UnsupportedOperationException("Options are not needed for this test");
        }

        @Override
        public Object executeScript(String script, Object... args) {
            this.script = script;
            this.arguments = args;
            return List.of();
        }

        @Override
        public Object executeAsyncScript(String script, Object... args) {
            throw new UnsupportedOperationException(
                    "Async script execution is not needed for this test");
        }
    }
}
