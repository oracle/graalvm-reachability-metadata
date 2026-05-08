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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Navigation;
import org.openqa.selenium.WebDriver.Options;
import org.openqa.selenium.WebDriver.TargetLocator;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ThreadGuard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ThreadGuardTest {
    @Test
    void protectCreatesDriverProxyThatDelegatesCallsOnOwningThread() {
        RecordingWebDriver driver = new RecordingWebDriver("https://example.test/current", "Thread Guard Page");

        WebDriver protectedDriver = ThreadGuard.protect(driver);

        assertThat(protectedDriver).isNotSameAs(driver);
        assertThat(protectedDriver.getCurrentUrl()).isEqualTo("https://example.test/current");
        assertThat(protectedDriver.getTitle()).isEqualTo("Thread Guard Page");
        assertThat(driver.calls()).containsExactly("getCurrentUrl", "getTitle");
    }

    @Test
    void protectedDriverRejectsCallsFromDifferentThread() {
        RecordingWebDriver driver = new RecordingWebDriver("https://example.test/current", "Thread Guard Page");
        WebDriver protectedDriver = ThreadGuard.protect(driver);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<String> title = executor.submit(protectedDriver::getTitle);

            assertThatThrownBy(() -> title.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(WebDriverException.class)
                    .hasStackTraceContaining("Thread safety error");
            assertThat(driver.calls()).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class RecordingWebDriver implements WebDriver {
        private final List<String> calls = new ArrayList<>();
        private final String currentUrl;
        private final String title;

        private RecordingWebDriver(String currentUrl, String title) {
            this.currentUrl = currentUrl;
            this.title = title;
        }

        @Override
        public void get(String url) {
            calls.add("get:" + url);
        }

        @Override
        public String getCurrentUrl() {
            calls.add("getCurrentUrl");
            return currentUrl;
        }

        @Override
        public String getTitle() {
            calls.add("getTitle");
            return title;
        }

        @Override
        public List<WebElement> findElements(By by) {
            return Collections.emptyList();
        }

        @Override
        public WebElement findElement(By by) {
            throw new UnsupportedOperationException("Elements are not needed by this test");
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
}
