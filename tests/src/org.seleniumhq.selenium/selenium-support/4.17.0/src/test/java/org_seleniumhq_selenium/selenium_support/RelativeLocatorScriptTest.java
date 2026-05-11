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
import org.openqa.selenium.WebDriver.Navigation;
import org.openqa.selenium.WebDriver.Options;
import org.openqa.selenium.WebDriver.TargetLocator;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.locators.RelativeLocator;

import static org.assertj.core.api.Assertions.assertThat;

public class RelativeLocatorScriptTest {
    @Test
    void relativeLocatorFindElementsLoadsClientSideScriptResource() {
        CapturingWebDriver context = new CapturingWebDriver();

        List<WebElement> elements = RelativeLocator.with(By.tagName("input"))
                .below(By.xpath("//*[@id='label']"))
                .findElements(context);

        assertThat(elements).isEmpty();
        assertThat(context.script())
                .startsWith("/* findElements */return (")
                .endsWith(").apply(null, arguments);");
        assertThat(context.arguments()).hasSize(1);
        assertThat(context.arguments().get(0))
                .isEqualTo(Map.of(
                        "relative", Map.of(
                                "root", Map.of("tag name", "input"),
                                "filters", List.of(Map.of(
                                        "kind", "below",
                                        "args", List.of(Map.of("xpath", "//*[@id='label']")))))));
    }

    private static final class CapturingWebDriver implements WebDriver, JavascriptExecutor {
        private String script;
        private List<Object> arguments = List.of();

        @Override
        public void get(String url) {
            throw new UnsupportedOperationException("Navigation is not used by this test");
        }

        @Override
        public String getCurrentUrl() {
            throw new UnsupportedOperationException("URL lookup is not used by this test");
        }

        @Override
        public String getTitle() {
            throw new UnsupportedOperationException("Title lookup is not used by this test");
        }

        @Override
        public List<WebElement> findElements(By by) {
            return List.of();
        }

        @Override
        public WebElement findElement(By by) {
            throw new UnsupportedOperationException("Element lookup is not used by this test");
        }

        @Override
        public String getPageSource() {
            throw new UnsupportedOperationException("Page source lookup is not used by this test");
        }

        @Override
        public void close() {
            // No resources are opened by this test double.
        }

        @Override
        public void quit() {
            // No resources are opened by this test double.
        }

        @Override
        public Set<String> getWindowHandles() {
            throw new UnsupportedOperationException("Window handles are not used by this test");
        }

        @Override
        public String getWindowHandle() {
            throw new UnsupportedOperationException("Window handle is not used by this test");
        }

        @Override
        public TargetLocator switchTo() {
            throw new UnsupportedOperationException("Frame switching is not used by this test");
        }

        @Override
        public Navigation navigate() {
            throw new UnsupportedOperationException("Navigation is not used by this test");
        }

        @Override
        public Options manage() {
            throw new UnsupportedOperationException("Options are not used by this test");
        }

        @Override
        public Object executeScript(String script, Object... args) {
            this.script = script;
            this.arguments = List.of(args);
            return List.of();
        }

        @Override
        public Object executeAsyncScript(String script, Object... args) {
            throw new UnsupportedOperationException("Async script execution is not used by this test");
        }

        String script() {
            return script;
        }

        List<Object> arguments() {
            return arguments;
        }
    }
}
