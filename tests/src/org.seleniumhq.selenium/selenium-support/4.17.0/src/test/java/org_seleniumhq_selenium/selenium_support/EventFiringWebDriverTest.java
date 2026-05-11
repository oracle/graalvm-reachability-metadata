/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Navigation;
import org.openqa.selenium.WebDriver.Options;
import org.openqa.selenium.WebDriver.TargetLocator;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.events.AbstractWebDriverEventListener;
import org.openqa.selenium.support.events.EventFiringWebDriver;

import static org.assertj.core.api.Assertions.assertThat;

public class EventFiringWebDriverTest {
    @Test
    void delegatesDriverCallsAndDispatchesNavigationEvents() {
        RecordingWebDriver driver = new RecordingWebDriver();
        RecordingEventListener listener = new RecordingEventListener();
        EventFiringWebDriver eventFiringDriver = new EventFiringWebDriver(driver)
                .register(listener);

        eventFiringDriver.get("https://example.test/page");
        String currentUrl = eventFiringDriver.getCurrentUrl();

        assertThat(currentUrl).isEqualTo("https://example.test/page");
        assertThat(driver.calls()).containsExactly(
                "get:https://example.test/page",
                "getCurrentUrl");
        assertThat(listener.events()).containsExactly(
                "beforeNavigateTo:https://example.test/page",
                "afterNavigateTo:https://example.test/page");
        assertThat(eventFiringDriver.getWrappedDriver()).isSameAs(driver);
    }

    private static final class RecordingWebDriver implements WebDriver {
        private final List<String> calls = new ArrayList<>();
        private String currentUrl;

        @Override
        public void get(String url) {
            calls.add("get:" + url);
            currentUrl = url;
        }

        @Override
        public String getCurrentUrl() {
            calls.add("getCurrentUrl");
            return currentUrl;
        }

        @Override
        public String getTitle() {
            return "Test Page";
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

        private List<String> calls() {
            return calls;
        }
    }

    private static final class RecordingEventListener extends AbstractWebDriverEventListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beforeNavigateTo(String url, WebDriver driver) {
            events.add("beforeNavigateTo:" + url);
        }

        @Override
        public void afterNavigateTo(String url, WebDriver driver) {
            events.add("afterNavigateTo:" + url);
        }

        private List<String> events() {
            return events;
        }
    }
}
