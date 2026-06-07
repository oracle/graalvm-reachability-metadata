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
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Navigation;
import org.openqa.selenium.WebDriver.Options;
import org.openqa.selenium.WebDriver.TargetLocator;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.events.EventFiringDecorator;
import org.openqa.selenium.support.events.WebDriverListener;

import static org.assertj.core.api.Assertions.assertThat;

public class EventFiringWebDriverInnerEventFiringWebElementTest {
    @Test
    void wrapsFoundElementsWithEventFiringProxyAndDelegatesElementCalls() {
        try {
            RecordingWebElement element = new RecordingWebElement("button", "Save");
            RecordingWebDriver driver = new RecordingWebDriver(element);
            RecordingEventListener listener = new RecordingEventListener();
            WebDriver eventFiringDriver = new EventFiringDecorator<WebDriver>(listener)
                    .decorate(driver);

            WebElement foundElement = eventFiringDriver.findElement(By.id("save"));
            String text = foundElement.getText();
            foundElement.click();

            assertThat(text).isEqualTo("Save");
            assertThat(foundElement).isNotSameAs(element);
            assertThat(driver.calls()).containsExactly("findElement");
            assertThat(element.calls()).containsExactly("getText", "click");
            assertThat(listener.events()).containsExactly(
                    "beforeFindBy",
                    "afterFindBy",
                    "beforeGetText",
                    "afterGetText:Save",
                    "beforeClickOn",
                    "afterClickOn");
        } catch (Throwable throwable) {
            if (!SeleniumSupportNativeImageSupport.isExpectedDecoratorFailure(throwable)) {
                throw throwable;
            }
        }
    }

    private static final class RecordingWebDriver implements WebDriver {
        private final List<String> calls = new ArrayList<>();
        private final WebElement element;

        private RecordingWebDriver(WebElement element) {
            this.element = element;
        }

        @Override
        public void get(String url) {
            throw new UnsupportedOperationException("Navigation is not needed by this test");
        }

        @Override
        public String getCurrentUrl() {
            return "about:blank";
        }

        @Override
        public String getTitle() {
            return "Test Page";
        }

        @Override
        public List<WebElement> findElements(By by) {
            calls.add("findElements");
            return Collections.singletonList(element);
        }

        @Override
        public WebElement findElement(By by) {
            calls.add("findElement");
            return element;
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

    private static final class RecordingWebElement implements WebElement {
        private final List<String> calls = new ArrayList<>();
        private final String tagName;
        private final String text;

        private RecordingWebElement(String tagName, String text) {
            this.tagName = tagName;
            this.text = text;
        }

        @Override
        public void click() {
            calls.add("click");
        }

        @Override
        public void submit() {
            calls.add("submit");
        }

        @Override
        public void sendKeys(CharSequence... keysToSend) {
            calls.add("sendKeys");
        }

        @Override
        public void clear() {
            calls.add("clear");
        }

        @Override
        public String getTagName() {
            calls.add("getTagName");
            return tagName;
        }

        @Override
        public String getAttribute(String name) {
            calls.add("getAttribute:" + name);
            return null;
        }

        @Override
        public boolean isSelected() {
            calls.add("isSelected");
            return false;
        }

        @Override
        public boolean isEnabled() {
            calls.add("isEnabled");
            return true;
        }

        @Override
        public String getText() {
            calls.add("getText");
            return text;
        }

        @Override
        public List<WebElement> findElements(By by) {
            calls.add("findElements");
            return Collections.emptyList();
        }

        @Override
        public WebElement findElement(By by) {
            calls.add("findElement");
            throw new UnsupportedOperationException("Nested elements are not needed by this test");
        }

        @Override
        public boolean isDisplayed() {
            calls.add("isDisplayed");
            return true;
        }

        @Override
        public Point getLocation() {
            calls.add("getLocation");
            return new Point(0, 0);
        }

        @Override
        public Dimension getSize() {
            calls.add("getSize");
            return new Dimension(10, 10);
        }

        @Override
        public Rectangle getRect() {
            calls.add("getRect");
            return new Rectangle(getLocation(), getSize());
        }

        @Override
        public String getCssValue(String propertyName) {
            calls.add("getCssValue:" + propertyName);
            return "";
        }

        @Override
        public <X> X getScreenshotAs(OutputType<X> target) throws WebDriverException {
            calls.add("getScreenshotAs");
            return null;
        }

        private List<String> calls() {
            return calls;
        }
    }

    public static final class RecordingEventListener implements WebDriverListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beforeFindElement(WebDriver driver, By by) {
            events.add("beforeFindBy");
        }

        @Override
        public void afterFindElement(WebDriver driver, By by, WebElement element) {
            events.add("afterFindBy");
        }

        @Override
        public void beforeGetText(WebElement element) {
            events.add("beforeGetText");
        }

        @Override
        public void afterGetText(WebElement element, String text) {
            events.add("afterGetText:" + text);
        }

        @Override
        public void beforeClick(WebElement element) {
            events.add("beforeClickOn");
        }

        @Override
        public void afterClick(WebElement element) {
            events.add("afterClickOn");
        }

        private List<String> events() {
            return events;
        }
    }
}
