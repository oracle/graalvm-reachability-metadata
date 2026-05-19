/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_support;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.locators.RelativeLocator;

import static org.assertj.core.api.Assertions.assertThat;

public class RelativeLocatorScriptTest {
    @Test
    void relativeLocatorFindElementsLoadsAndExecutesBundledJavascript() {
        RecordingJavascriptDriver driver = new RecordingJavascriptDriver();

        List<WebElement> elements = RelativeLocator.with(By.tagName("button"))
                .near(By.cssSelector(".primary"))
                .findElements(driver);

        assertThat(elements).isSameAs(driver.resultElements);
        assertThat(driver.executedScript)
                .startsWith("/* findElements */return (")
                .contains("function")
                .contains("arguments");
        assertThat(driver.executedArguments).hasSize(1);
    }

    private static final class RecordingJavascriptDriver implements WebDriver, JavascriptExecutor {
        private final List<WebElement> resultElements = List.of();
        private String executedScript;
        private Object[] executedArguments;

        @Override
        public Object executeScript(String script, Object... args) {
            executedScript = script;
            executedArguments = args;
            return resultElements;
        }

        @Override
        public Object executeAsyncScript(String script, Object... args) {
            throw new UnsupportedOperationException("Async JavaScript is not used by this test");
        }

        @Override
        public void get(String url) {
            throw new UnsupportedOperationException("Navigation is not used by this test");
        }

        @Override
        public String getCurrentUrl() {
            throw new UnsupportedOperationException("Current URL is not used by this test");
        }

        @Override
        public String getTitle() {
            throw new UnsupportedOperationException("Title is not used by this test");
        }

        @Override
        public List<WebElement> findElements(By by) {
            return List.of();
        }

        @Override
        public WebElement findElement(By by) {
            throw new NoSuchElementException("No elements are exposed by this test driver");
        }

        @Override
        public String getPageSource() {
            throw new UnsupportedOperationException("Page source is not used by this test");
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException("Close is not used by this test");
        }

        @Override
        public void quit() {
            throw new UnsupportedOperationException("Quit is not used by this test");
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
    }
}
