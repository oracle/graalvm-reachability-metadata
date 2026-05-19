/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_support;

import java.util.Collections;
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
    void relativeLocatorLoadsScriptResourceBeforeExecutingJavascript() {
        RecordingJavascriptWebDriver driver = new RecordingJavascriptWebDriver();

        List<WebElement> elements = RelativeLocator.with(By.tagName("button"))
                .below(By.id("header"))
                .findElements(driver);

        assertThat(elements).isEmpty();
        assertThat(driver.executedScript()).startsWith("/* findElements */return (");
        assertThat(driver.executedScript()).contains("arguments");
        assertThat(driver.scriptArguments()).hasSize(1);
        assertThat(driver.scriptArguments().get(0)).isInstanceOf(Map.class);
    }

    public static final class RecordingJavascriptWebDriver
            implements WebDriver, JavascriptExecutor {
        private String executedScript;
        private List<Object> scriptArguments = List.of();

        @Override
        public Object executeScript(String script, Object... args) {
            executedScript = script;
            scriptArguments = List.of(args);
            return Collections.emptyList();
        }

        @Override
        public Object executeAsyncScript(String script, Object... args) {
            throw new UnsupportedOperationException("Async scripts are not needed by this test");
        }

        @Override
        public void get(String url) {
        }

        @Override
        public String getCurrentUrl() {
            return "";
        }

        @Override
        public String getTitle() {
            return "";
        }

        @Override
        public List<WebElement> findElements(By by) {
            return Collections.emptyList();
        }

        @Override
        public WebElement findElement(By by) {
            throw new UnsupportedOperationException(
                    "No elements are available in this test driver");
        }

        @Override
        public String getPageSource() {
            return "";
        }

        @Override
        public void close() {
        }

        @Override
        public void quit() {
        }

        @Override
        public Set<String> getWindowHandles() {
            return Collections.emptySet();
        }

        @Override
        public String getWindowHandle() {
            return "window";
        }

        @Override
        public TargetLocator switchTo() {
            throw new UnsupportedOperationException("Target locator is not needed by this test");
        }

        @Override
        public Navigation navigate() {
            throw new UnsupportedOperationException("Navigation object is not needed by this test");
        }

        @Override
        public Options manage() {
            throw new UnsupportedOperationException("Options are not needed by this test");
        }

        private String executedScript() {
            return executedScript;
        }

        private List<Object> scriptArguments() {
            return scriptArguments;
        }
    }
}
